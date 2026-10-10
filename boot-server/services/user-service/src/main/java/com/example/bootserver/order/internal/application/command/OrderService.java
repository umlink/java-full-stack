package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.catalog.api.CatalogQueryApi;
import com.example.bootserver.catalog.api.OrderableSkuQuote;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.inventory.api.InventoryReservationResult;
import com.example.bootserver.order.internal.domain.ExistingOrder;
import com.example.bootserver.order.internal.domain.IdempotencyKey;
import com.example.bootserver.order.internal.domain.Order;
import com.example.bootserver.order.internal.domain.OrderLine;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderRequestFingerprint;
import com.example.bootserver.order.internal.domain.OrderSelection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 无事务的幂等协调入口；唯一键竞争后只能在失败写事务结束后回查原单。 */
@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderWriteStepService writeStep;
    private final CatalogQueryApi catalog;
    private final InventoryCommandApi inventory;
    private final OrderNumberGenerator numbers;

    public OrderService(OrderRepository orders, OrderWriteStepService writeStep,
                        CatalogQueryApi catalog, InventoryCommandApi inventory,
                        OrderNumberGenerator numbers) {
        this.orders = orders;
        this.writeStep = writeStep;
        this.catalog = catalog;
        this.inventory = inventory;
        this.numbers = numbers;
    }

    public CreatedOrder createOrder(Long userId, String rawKey, List<OrderSelection> items) {
        IdempotencyKey key;
        OrderRequestFingerprint fingerprint;
        try {
            key = new IdempotencyKey(rawKey);
            if (items == null || items.isEmpty() || items.size() > 50) {
                throw new IllegalArgumentException("订单明细数量必须在 1 到 50 之间");
            }
            // 先校验结构和去重，再计算请求语义；重放不依赖实时价格、商品状态或库存。
            fingerprint = OrderRequestFingerprint.from(userId, items);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, exception.getMessage());
        }

        ExistingOrder existing = orders.getByUserIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing != null) {
            return replayIfSame(existing, fingerprint);
        }
        Order order = prepareOrder(userId, key, fingerprint, items);
        InventoryReservationId reservationId = OrderReservationIds.fromIdempotencyKey(userId, key.value());
        List<OrderLine> attempted = new ArrayList<>();
        try {
            preReserve(reservationId, order.lines(), attempted);
            CreatedOrder created = writeStep.createOrder(order, reservationId);
            complete(reservationId, attempted);
            return created;
        } catch (DuplicateKeyException exception) {
            // 唯一键可能是同键竞争，也可能是订单号碰撞；仅找到相同键的原单时才可重放。
            ExistingOrder concurrent = orders.getByUserIdAndIdempotencyKey(userId, key).orElse(null);
            if (concurrent == null) {
                compensate(reservationId, attempted);
                throw exception;
            }
            try {
                CreatedOrder replay = replayIfSame(concurrent, fingerprint);
                complete(reservationId, attempted);
                return replay;
            } catch (RuntimeException conflict) {
                compensate(reservationId, attempted);
                throw conflict;
            }
        } catch (RuntimeException exception) {
            compensate(reservationId, attempted);
            throw exception;
        }
    }

    private Order prepareOrder(Long userId, IdempotencyKey key, OrderRequestFingerprint fingerprint,
                               List<OrderSelection> selections) {
        Set<Long> skuIds = selections.stream().map(OrderSelection::skuId).collect(Collectors.toSet());
        Map<Long, OrderableSkuQuote> quotes = catalog.listOrderableSkuQuotes(skuIds).stream()
                .collect(Collectors.toMap(OrderableSkuQuote::skuId, Function.identity()));
        if (quotes.size() != skuIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在或未上架");
        }
        try {
            List<OrderLine> lines = selections.stream()
                    .map(item -> new OrderLine(item.skuId(), item.quantity(),
                            quotes.get(item.skuId()).unitPrice()))
                    .toList();
            return Order.create(userId, numbers.nextOrderNo(), key, fingerprint, lines);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, exception.getMessage());
        }
    }

    private void preReserve(InventoryReservationId reservationId, List<OrderLine> lines,
                            List<OrderLine> attempted) {
        for (OrderLine line : lines) {
            InventoryReservationResult result = inventory.preReserve(
                    reservationId, line.skuId(), line.quantity());
            if (result == InventoryReservationResult.INSUFFICIENT) {
                throw new BusinessException(ErrorCode.CONFLICT, "库存未配置或可用量不足");
            }
            if (result == InventoryReservationResult.CONFLICT) {
                throw new BusinessException(ErrorCode.CONFLICT, "幂等键已用于不同的库存预占");
            }
            attempted.add(line);
        }
    }

    private void complete(InventoryReservationId reservationId, List<OrderLine> lines) {
        lines.forEach(line -> inventory.completeReservation(
                reservationId, line.skuId(), line.quantity()));
    }

    private void compensate(InventoryReservationId reservationId, List<OrderLine> lines) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            OrderLine line = lines.get(index);
            inventory.compensateReservation(reservationId, line.skuId(), line.quantity());
        }
    }

    private CreatedOrder replayIfSame(ExistingOrder order, OrderRequestFingerprint fingerprint) {
        if (!order.fingerprint().equals(fingerprint)) {
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键已用于不同的下单内容");
        }
        return new CreatedOrder(order.id(), order.orderNo(), order.status(), order.totalAmount());
    }
}
