package com.example.bootserver.order.internal.infrastructure.messaging;

import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.application.command.OrderTimeoutDispatchTask;
import com.example.bootserver.order.internal.application.command.OrderTimeoutPublisher;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 把 Outbox 快照转换为带稳定 key、版本 tag 和延时等级的 RocketMQ 消息。 */
@Component
@ConditionalOnProperty(prefix = "app.order-timeout.rocketmq", name = "enabled", havingValue = "true")
public class RocketMqOrderTimeoutPublisher implements OrderTimeoutPublisher {

    private final DefaultMQProducer producer;
    private final RocketMqOrderTimeoutProperties properties;

    public RocketMqOrderTimeoutPublisher(DefaultMQProducer producer,
                                         RocketMqOrderTimeoutProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void publish(OrderTimeoutDispatchTask task) throws Exception {
        Message message = new Message(properties.getTopic(), OrderTimeoutEvent.MESSAGE_TAG,
                task.businessKey(), task.payload().getBytes(StandardCharsets.UTF_8));
        message.setDelayTimeLevel(properties.getDelayLevel());
        SendResult result = producer.send(message, properties.getSendTimeout().toMillis());
        if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("RocketMQ 未确认订单超时消息发送成功");
        }
    }
}
