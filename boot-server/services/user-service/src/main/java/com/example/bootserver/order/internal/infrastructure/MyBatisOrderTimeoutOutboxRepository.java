package com.example.bootserver.order.internal.infrastructure;

import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.application.command.OrderTimeoutDispatchTask;
import com.example.bootserver.order.internal.application.command.OrderTimeoutOutboxRepository;
import com.example.bootserver.order.internal.application.command.OrderTimeoutTaskRecord;
import com.example.bootserver.order.internal.domain.OrderTimeoutTaskStatus;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MyBatis Outbox 适配器；应用层决定事务和重试策略。 */
@Repository
public class MyBatisOrderTimeoutOutboxRepository implements OrderTimeoutOutboxRepository {

    private final OrderTimeoutOutboxMapper mapper;
    private final ObjectMapper json;

    public MyBatisOrderTimeoutOutboxRepository(OrderTimeoutOutboxMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public void insert(OrderTimeoutEvent event, LocalDateTime now) {
        OrderTimeoutOutboxEntity entity = new OrderTimeoutOutboxEntity();
        entity.setEventId(event.eventId());
        entity.setBusinessKey(event.businessKey());
        entity.setOrderId(event.orderId());
        entity.setEventVersion(event.version());
        entity.setPayload(json.writeValueAsString(event));
        entity.setStatus(OrderTimeoutTaskStatus.PENDING.name());
        entity.setSendAttempts(0);
        entity.setNextAttemptTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        mapper.insert(entity);
    }

    @Override
    public int recoverStaleSending(LocalDateTime staleBefore, LocalDateTime now, int maxAttempts) {
        return mapper.recoverStaleSending(staleBefore, now, maxAttempts);
    }

    @Override
    public List<OrderTimeoutDispatchTask> listDispatchable(LocalDateTime now, int maxAttempts, int batchSize) {
        return mapper.selectDispatchable(now, maxAttempts, batchSize).stream()
                .map(entity -> new OrderTimeoutDispatchTask(entity.getId(), entity.getEventId(),
                        entity.getBusinessKey(), entity.getOrderId(), entity.getPayload(),
                        entity.getSendAttempts()))
                .toList();
    }

    @Override
    public boolean claimForSend(Long id, LocalDateTime now, int maxAttempts) {
        return mapper.claimForSend(id, now, maxAttempts) == 1;
    }

    @Override
    public boolean markSent(Long id, LocalDateTime now) {
        return mapper.markSent(id, now) == 1;
    }

    @Override
    public boolean markDeadAfterFailure(Long id, String error, LocalDateTime now, int maxAttempts) {
        return mapper.markDeadAfterFailure(id, error, now, maxAttempts) == 1;
    }

    @Override
    public boolean markPendingAfterFailure(Long id, String error, LocalDateTime nextAttempt,
                                           LocalDateTime now, int maxAttempts) {
        return mapper.markPendingAfterFailure(id, error, nextAttempt, now, maxAttempts) == 1;
    }

    @Override
    public Optional<OrderTimeoutTaskRecord> getByEventId(String eventId) {
        return Optional.ofNullable(mapper.selectByEventId(eventId))
                .map(entity -> new OrderTimeoutTaskRecord(entity.getEventId(), entity.getBusinessKey(),
                        entity.getOrderId(), OrderTimeoutTaskStatus.valueOf(entity.getStatus())));
    }

    @Override
    public boolean claimForConsume(String eventId, LocalDateTime now) {
        return mapper.claimForConsume(eventId, now) == 1;
    }

    @Override
    public boolean markConsumed(String eventId, OrderTimeoutTaskStatus target, LocalDateTime now) {
        if (target != OrderTimeoutTaskStatus.PROCESSED && target != OrderTimeoutTaskStatus.IGNORED) {
            throw new IllegalArgumentException("Outbox 消费终态不合法");
        }
        return mapper.markConsumed(eventId, target.name(), now) == 1;
    }
}
