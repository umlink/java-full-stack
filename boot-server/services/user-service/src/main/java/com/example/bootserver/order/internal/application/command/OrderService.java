package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.order.internal.domain.ExistingOrder;
import com.example.bootserver.order.internal.domain.IdempotencyKey;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderRequestFingerprint;
import com.example.bootserver.order.internal.domain.OrderSelection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/** 无事务的幂等协调入口；唯一键竞争后只能在失败写事务结束后回查原单。 */
@Service
public class OrderService {
    private final OrderRepository orders;
    private final OrderWriteStepService writeStep;

    public OrderService(OrderRepository orders, OrderWriteStepService writeStep) {
        this.orders = orders;
        this.writeStep = writeStep;
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
        try {
            return writeStep.createOrder(userId, key, fingerprint, List.copyOf(items));
        } catch (DuplicateKeyException exception) {
            // 唯一键可能是同键竞争，也可能是订单号碰撞；仅找到相同键的原单时才可重放。
            return orders.getByUserIdAndIdempotencyKey(userId, key)
                    .map(order -> replayIfSame(order, fingerprint))
                    .orElseThrow(() -> exception);
        }
    }

    private CreatedOrder replayIfSame(ExistingOrder order, OrderRequestFingerprint fingerprint) {
        if (!order.fingerprint().equals(fingerprint)) {
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键已用于不同的下单内容");
        }
        return new CreatedOrder(order.id(), order.orderNo(), order.status(), order.totalAmount());
    }
}
