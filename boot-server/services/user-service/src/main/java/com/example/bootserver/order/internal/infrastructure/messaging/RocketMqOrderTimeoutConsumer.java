package com.example.bootserver.order.internal.infrastructure.messaging;

import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.application.command.OrderTimeoutHandlingResult;
import com.example.bootserver.order.internal.application.command.OrderTimeoutMetrics;
import com.example.bootserver.order.internal.application.command.OrderTimeoutService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** RocketMQ 至应用命令的协议适配器；失败返回稍后重试，由 Broker 执行有界重投和死信。 */
public class RocketMqOrderTimeoutConsumer implements MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(RocketMqOrderTimeoutConsumer.class);

    private final OrderTimeoutService service;
    private final ObjectMapper json;
    private final RocketMqOrderTimeoutProperties properties;
    private final MeterRegistry meters;

    public RocketMqOrderTimeoutConsumer(OrderTimeoutService service, ObjectMapper json,
                                        RocketMqOrderTimeoutProperties properties,
                                        MeterRegistry meters) {
        this.service = service;
        this.json = json;
        this.properties = properties;
        this.meters = meters;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                                                     ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            try {
                OrderTimeoutEvent event = json.readValue(message.getBody(), OrderTimeoutEvent.class);
                OrderTimeoutHandlingResult result = service.consume(event);
                counter("consume", result.name().toLowerCase(Locale.ROOT)).increment();
                recordDelay(event.createdAt());
                log.info("订单超时消息消费完成，eventId={}, businessKey={}, orderId={}, result={}",
                        event.eventId(), event.businessKey(), event.orderId(), result);
            } catch (RuntimeException exception) {
                boolean finalRetry = message.getReconsumeTimes() >= properties.getMaxReconsumeTimes();
                counter("consume", finalRetry ? "dead_letter" : "retry").increment();
                log.warn("订单超时消息消费失败，messageId={}, reconsumeTimes={}, finalRetry={}",
                        message.getMsgId(), message.getReconsumeTimes(), finalRetry, exception);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private void recordDelay(Instant createdAt) {
        Duration delay = Duration.between(createdAt, Instant.now());
        Timer.builder(OrderTimeoutMetrics.PROCESSING_DELAY).register(meters)
                .record(delay.isNegative() ? Duration.ZERO : delay);
    }

    private Counter counter(String operation, String result) {
        return Counter.builder(OrderTimeoutMetrics.OPERATIONS)
                .tag(OrderTimeoutMetrics.OPERATION_TAG, operation)
                .tag(OrderTimeoutMetrics.RESULT_TAG, result)
                .register(meters);
    }
}
