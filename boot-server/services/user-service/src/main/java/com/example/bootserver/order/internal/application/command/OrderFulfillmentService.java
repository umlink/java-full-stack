package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.order.internal.domain.IllegalOrderStateException;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 发货与确认收货状态用例；条件更新负责并发下的唯一合法迁移。 */
@Service
public class OrderFulfillmentService {
    private final OrderRepository orders;

    public OrderFulfillmentService(OrderRepository orders) {
        this.orders = orders;
    }

    @Transactional
    public void ship(Long orderId) {
        OrderStatus target = OrderStatus.PAID.ship();
        if (orders.updateStatusIfCurrent(orderId, OrderStatus.PAID, target) == 0) {
            OrderStatus current = orders.getStatusById(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
            rejectIllegalTransition(current, OrderStatus::ship);
        }
    }

    @Transactional
    public void confirm(Long userId, Long orderId) {
        OrderStatus target = OrderStatus.SHIPPED.confirm();
        if (orders.updateStatusIfCurrent(orderId, userId, OrderStatus.SHIPPED, target) == 0) {
            OrderStatus current = orders.getStatusByIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
            rejectIllegalTransition(current, OrderStatus::confirm);
        }
    }

    private void rejectIllegalTransition(OrderStatus current,
                                         java.util.function.Function<OrderStatus, OrderStatus> transition) {
        try {
            transition.apply(current);
        } catch (IllegalOrderStateException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage());
        }
        throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变化，请重试");
    }
}
