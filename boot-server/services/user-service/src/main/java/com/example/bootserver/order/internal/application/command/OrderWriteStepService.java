package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.order.internal.domain.IdempotencyKey;
import com.example.bootserver.order.internal.domain.Order;
import com.example.bootserver.order.internal.domain.OrderLine;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderRequestFingerprint;
import com.example.bootserver.order.internal.domain.OrderSelection;
import com.example.bootserver.catalog.api.CatalogQueryApi;
import com.example.bootserver.catalog.api.OrderableSkuQuote;
import com.example.bootserver.inventory.api.InventoryCommandApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 独立写事务：订单唯一键、明细和预扣同时提交或回滚。 */
@Service
public class OrderWriteStepService {
    private final CatalogQueryApi catalog;
    private final InventoryCommandApi inventory;
    private final OrderRepository orders;
    private final OrderNumberGenerator numbers;

    public OrderWriteStepService(CatalogQueryApi catalog, InventoryCommandApi inventory,
                                 OrderRepository orders, OrderNumberGenerator numbers) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.orders = orders;
        this.numbers = numbers;
    }

    @Transactional
    public CreatedOrder createOrder(Long userId, IdempotencyKey key, OrderRequestFingerprint fingerprint,
                                    List<OrderSelection> items) {
        Set<Long> skuIds = items.stream().map(OrderSelection::skuId).collect(Collectors.toSet());
        Map<Long, OrderableSkuQuote> quotes = catalog.listOrderableSkuQuotes(skuIds).stream()
                .collect(Collectors.toMap(OrderableSkuQuote::skuId, Function.identity()));
        if (quotes.size() != skuIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在或未上架");
        }
        try {
            List<OrderLine> lines = items.stream()
                    .map(item -> new OrderLine(item.skuId(), item.quantity(),
                            quotes.get(item.skuId()).unitPrice()))
                    .toList();
            Order order = Order.create(userId, numbers.nextOrderNo(), key, fingerprint, lines);
            Long id = orders.insert(order);
            for (OrderLine line : lines) {
                if (!inventory.reserveIfAvailable(line.skuId(), line.quantity())) {
                    throw new BusinessException(ErrorCode.CONFLICT, "库存未配置或可用量不足");
                }
            }
            return new CreatedOrder(id, order.orderNo(), order.status(), order.totalAmount());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, exception.getMessage());
        }
    }
}
