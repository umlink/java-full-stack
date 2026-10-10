package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.order.internal.domain.Order;
import com.example.bootserver.order.internal.domain.OrderLine;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.inventory.api.InventoryCommandApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 独立写事务：订单唯一键、明细和预扣同时提交或回滚。 */
@Service
public class OrderWriteStepService {
    private final InventoryCommandApi inventory;
    private final OrderRepository orders;
    private final OrderTimeoutOutboxService timeouts;

    public OrderWriteStepService(InventoryCommandApi inventory, OrderRepository orders,
                                 OrderTimeoutOutboxService timeouts) {
        this.inventory = inventory;
        this.orders = orders;
        this.timeouts = timeouts;
    }

    @Transactional
    public CreatedOrder createOrder(Order order, InventoryReservationId reservationId) {
        Long id = orders.insert(order);
        for (OrderLine line : order.lines()) {
            if (!inventory.persistReservation(reservationId, line.skuId(), line.quantity())) {
                throw new BusinessException(ErrorCode.CONFLICT, "库存未配置或可用量不足");
            }
        }
        timeouts.schedule(id);
        return new CreatedOrder(id, order.orderNo(), order.status(), order.totalAmount());
    }
}
