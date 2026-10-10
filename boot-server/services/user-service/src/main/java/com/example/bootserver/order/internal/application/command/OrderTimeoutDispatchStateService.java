package com.example.bootserver.order.internal.application.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 用短数据库事务领取一次发送尝试并记录结果，网络发送在这些事务之外执行。 */
@Service
public class OrderTimeoutDispatchStateService {

    private static final int LAST_ERROR_MAX_LENGTH = 512;

    private final OrderTimeoutOutboxRepository outbox;

    public OrderTimeoutDispatchStateService(OrderTimeoutOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public int recoverStaleSending(LocalDateTime now, java.time.Duration lease, int maxAttempts) {
        return outbox.recoverStaleSending(now.minus(lease), now, maxAttempts);
    }

    @Transactional(readOnly = true)
    public List<OrderTimeoutDispatchTask> listReady(LocalDateTime now, int maxAttempts, int batchSize) {
        return outbox.listDispatchable(now, maxAttempts, batchSize);
    }

    @Transactional
    public boolean claim(Long id, LocalDateTime now, int maxAttempts) {
        return outbox.claimForSend(id, now, maxAttempts);
    }

    @Transactional
    public boolean sent(Long id, LocalDateTime now) {
        return outbox.markSent(id, now);
    }

    @Transactional
    public boolean failed(Long id, Throwable failure, LocalDateTime now,
                          int maxAttempts, java.time.Duration retryBackoff) {
        String error = summarize(failure);
        if (outbox.markDeadAfterFailure(id, error, now, maxAttempts)) {
            return true;
        }
        LocalDateTime nextAttempt = now.plus(retryBackoff);
        if (!outbox.markPendingAfterFailure(id, error, nextAttempt, now,
                maxAttempts)) {
            throw new IllegalStateException("Outbox 发送失败状态无法推进", failure);
        }
        return false;
    }

    private String summarize(Throwable failure) {
        String message = failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        return message.length() <= LAST_ERROR_MAX_LENGTH
                ? message : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
