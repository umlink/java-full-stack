package com.example.bootserver.order.internal.infrastructure.messaging;

import com.example.bootserver.order.internal.application.command.OrderTimeoutDispatchStateService;
import com.example.bootserver.order.internal.application.command.OrderTimeoutDispatchTask;
import com.example.bootserver.order.internal.application.command.OrderTimeoutMetrics;
import com.example.bootserver.order.internal.application.command.OrderTimeoutPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 周期轮询 Outbox；数据库短事务领取后，在事务外执行 MQ 网络发送。 */
@Component
@ConditionalOnProperty(prefix = "app.order-timeout.rocketmq", name = "enabled", havingValue = "true")
public class OrderTimeoutOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutOutboxDispatcher.class);

    private final OrderTimeoutDispatchStateService states;
    private final OrderTimeoutPublisher publisher;
    private final RocketMqOrderTimeoutProperties properties;
    private final MeterRegistry meters;

    public OrderTimeoutOutboxDispatcher(OrderTimeoutDispatchStateService states,
                                        OrderTimeoutPublisher publisher,
                                        RocketMqOrderTimeoutProperties properties,
                                        MeterRegistry meters) {
        this.states = states;
        this.publisher = publisher;
        this.properties = properties;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${app.order-timeout.rocketmq.poll-interval}",
            initialDelayString = "${app.order-timeout.rocketmq.poll-interval}")
    public void dispatchReady() {
        LocalDateTime now = LocalDateTime.now();
        int recovered = states.recoverStaleSending(now, properties.getSendingLease(),
                properties.getMaxSendAttempts());
        if (recovered > 0) {
            counter("recovery", "recovered").increment(recovered);
        }
        for (OrderTimeoutDispatchTask task : states.listReady(now,
                properties.getMaxSendAttempts(), properties.getBatchSize())) {
            dispatch(task);
        }
    }

    private void dispatch(OrderTimeoutDispatchTask task) {
        LocalDateTime claimedAt = LocalDateTime.now();
        if (!states.claim(task.id(), claimedAt, properties.getMaxSendAttempts())) {
            counter("publish", "claim_conflict").increment();
            return;
        }
        try {
            publisher.publish(task);
            if (!states.sent(task.id(), LocalDateTime.now())) {
                counter("publish", "state_changed").increment();
                return;
            }
            counter("publish", "sent").increment();
            log.info("订单超时消息已发送，eventId={}, businessKey={}, orderId={}",
                    task.eventId(), task.businessKey(), task.orderId());
        } catch (Exception exception) {
            boolean dead = states.failed(task.id(), exception, LocalDateTime.now(),
                    properties.getMaxSendAttempts(), properties.getRetryBackoff());
            counter("publish", dead ? "dead" : "retry").increment();
            log.warn("订单超时消息发送失败，eventId={}, businessKey={}, orderId={}, dead={}",
                    task.eventId(), task.businessKey(), task.orderId(), dead, exception);
        }
    }

    private Counter counter(String operation, String result) {
        return Counter.builder(OrderTimeoutMetrics.OPERATIONS)
                .tag(OrderTimeoutMetrics.OPERATION_TAG, operation)
                .tag(OrderTimeoutMetrics.RESULT_TAG, result)
                .register(meters);
    }
}
