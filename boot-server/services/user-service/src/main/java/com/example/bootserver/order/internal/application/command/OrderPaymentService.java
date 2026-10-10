package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.order.api.OrderPaymentApi;
import com.example.bootserver.order.api.PayableOrder;
import com.example.bootserver.order.internal.domain.IllegalOrderStateException;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderStatus;
import org.springframework.stereotype.Service;

/** 向支付上下文提供订单状态认领与支付快照，订单表仍只由订单上下文修改。 */
@Service
public class OrderPaymentService implements OrderPaymentApi {
    private final OrderRepository orders;
    private final OrderCommandSnapshotRepository snapshots;

    public OrderPaymentService(OrderRepository orders, OrderCommandSnapshotRepository snapshots) {
        this.orders = orders;
        this.snapshots = snapshots;
    }

    @Override
    public PayableOrder claimForPayment(Long userId, Long orderId) {
        OrderStatus target = OrderStatus.CREATED.pay();
        if (orders.updateStatusIfCurrent(orderId, userId, OrderStatus.CREATED, target) == 0) {
            OrderStatus current = orders.getStatusByIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
            try {
                current.pay();
            } catch (IllegalOrderStateException exception) {
                throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage());
            }
            throw new BusinessException(ErrorCode.CONFLICT, "订单状态已变化，请重试");
        }
        OrderCommandSnapshot order = snapshots.getCommandSnapshotByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
        return new PayableOrder(order.id(), order.totalAmount(), order.items().stream()
                .map(item -> new PayableOrder.Item(item.skuId(), item.quantity()))
                .toList());
    }
}
