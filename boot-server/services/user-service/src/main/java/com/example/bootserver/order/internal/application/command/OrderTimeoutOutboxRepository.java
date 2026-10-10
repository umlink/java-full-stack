package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.domain.OrderTimeoutTaskStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Outbox 持久化端口；发送与消费只通过条件状态迁移推进记录。 */
public interface OrderTimeoutOutboxRepository {

    void insert(OrderTimeoutEvent event, LocalDateTime now);

    int recoverStaleSending(LocalDateTime staleBefore, LocalDateTime now, int maxAttempts);

    List<OrderTimeoutDispatchTask> listDispatchable(LocalDateTime now, int maxAttempts, int batchSize);

    boolean claimForSend(Long id, LocalDateTime now, int maxAttempts);

    boolean markSent(Long id, LocalDateTime now);

    boolean markDeadAfterFailure(Long id, String error, LocalDateTime now, int maxAttempts);

    boolean markPendingAfterFailure(Long id, String error, LocalDateTime nextAttempt, LocalDateTime now,
                                    int maxAttempts);

    Optional<OrderTimeoutTaskRecord> getByEventId(String eventId);

    boolean claimForConsume(String eventId, LocalDateTime now);

    boolean markConsumed(String eventId, OrderTimeoutTaskStatus target, LocalDateTime now);
}
