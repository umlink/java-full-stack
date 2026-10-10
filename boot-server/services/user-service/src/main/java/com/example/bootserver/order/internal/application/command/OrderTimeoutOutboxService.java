package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.order.api.OrderTimeoutEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;

/** 在调用方订单事务内创建版本化超时事件和 Outbox 事实。 */
@Service
public class OrderTimeoutOutboxService {

    private final OrderTimeoutOutboxRepository outbox;

    public OrderTimeoutOutboxService(OrderTimeoutOutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void schedule(Long orderId) {
        OrderTimeoutEvent event = OrderTimeoutEvent.create(orderId, Instant.now());
        outbox.insert(event, LocalDateTime.now());
    }
}
