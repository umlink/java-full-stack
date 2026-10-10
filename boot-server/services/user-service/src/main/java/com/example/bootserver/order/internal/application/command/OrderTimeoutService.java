package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.domain.OrderRepository;
import com.example.bootserver.order.internal.domain.OrderStatus;
import com.example.bootserver.order.internal.domain.OrderTimeoutTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 消费事务：同一数据库边界内幂等关单、释放锁定库存并完成 Outbox。 */
@Service
public class OrderTimeoutService {

    private final OrderTimeoutOutboxRepository outbox;
    private final OrderRepository orders;
    private final OrderCommandSnapshotRepository snapshots;
    private final InventoryCommandApi inventory;

    public OrderTimeoutService(OrderTimeoutOutboxRepository outbox, OrderRepository orders,
                               OrderCommandSnapshotRepository snapshots, InventoryCommandApi inventory) {
        this.outbox = outbox;
        this.orders = orders;
        this.snapshots = snapshots;
        this.inventory = inventory;
    }

    @Transactional
    public OrderTimeoutHandlingResult consume(OrderTimeoutEvent event) {
        OrderTimeoutTaskRecord task = requireMatchingTask(event);
        if (task.status().isConsumed()) {
            return OrderTimeoutHandlingResult.DUPLICATE;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!outbox.claimForConsume(event.eventId(), now)) {
            OrderTimeoutTaskRecord latest = requireMatchingTask(event);
            if (latest.status().isConsumed()) {
                return OrderTimeoutHandlingResult.DUPLICATE;
            }
            throw new IllegalStateException("订单超时事件无法领取消费权");
        }

        OrderStatus cancelled = OrderStatus.CREATED.cancel();
        if (orders.updateStatusIfCurrent(event.orderId(), OrderStatus.CREATED, cancelled) == 1) {
            OrderCommandSnapshot order = snapshots.getCommandSnapshotById(event.orderId())
                    .orElseThrow(() -> new IllegalStateException("超时订单快照不存在"));
            order.items().forEach(item -> inventory.releaseReserved(
                    order.reservationId(), item.skuId(), item.quantity()));
            requireTerminalUpdate(event.eventId(), OrderTimeoutTaskStatus.PROCESSED, now);
            return OrderTimeoutHandlingResult.PROCESSED;
        }

        OrderStatus current = orders.getStatusById(event.orderId())
                .orElseThrow(() -> new IllegalStateException("超时订单不存在"));
        if (current == OrderStatus.CREATED) {
            throw new IllegalStateException("超时订单状态并发更新失败");
        }
        requireTerminalUpdate(event.eventId(), OrderTimeoutTaskStatus.IGNORED, now);
        return OrderTimeoutHandlingResult.IGNORED;
    }

    private OrderTimeoutTaskRecord requireMatchingTask(OrderTimeoutEvent event) {
        OrderTimeoutTaskRecord task = outbox.getByEventId(event.eventId())
                .orElseThrow(() -> new IllegalArgumentException("订单超时事件没有对应 Outbox"));
        if (!task.businessKey().equals(event.businessKey()) || !task.orderId().equals(event.orderId())) {
            throw new IllegalArgumentException("订单超时事件与 Outbox 身份不一致");
        }
        return task;
    }

    private void requireTerminalUpdate(String eventId, OrderTimeoutTaskStatus status, LocalDateTime now) {
        if (!outbox.markConsumed(eventId, status, now)) {
            throw new IllegalStateException("订单超时事件终态写入失败");
        }
    }
}
