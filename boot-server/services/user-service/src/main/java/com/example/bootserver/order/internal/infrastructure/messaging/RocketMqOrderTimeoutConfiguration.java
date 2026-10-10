package com.example.bootserver.order.internal.infrastructure.messaging;

import com.example.bootserver.order.api.OrderTimeoutEvent;
import com.example.bootserver.order.internal.application.command.OrderTimeoutService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

/** 注册 Outbox 调度与 RocketMQ 配置；具体客户端仅在开关启用时装配。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RocketMqOrderTimeoutProperties.class)
public class RocketMqOrderTimeoutConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.order-timeout.rocketmq", name = "enabled", havingValue = "true")
    DefaultMQProducer orderTimeoutProducer(RocketMqOrderTimeoutProperties properties) throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNamesrvAddr());
        producer.setRetryTimesWhenSendFailed(0);
        producer.setRetryTimesWhenSendAsyncFailed(0);
        producer.start();
        return producer;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.order-timeout.rocketmq", name = "enabled", havingValue = "true")
    RocketMqOrderTimeoutConsumer orderTimeoutListener(OrderTimeoutService service, ObjectMapper json,
                                                       RocketMqOrderTimeoutProperties properties,
                                                       MeterRegistry meters) {
        return new RocketMqOrderTimeoutConsumer(service, json, properties, meters);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.order-timeout.rocketmq", name = "enabled", havingValue = "true")
    DefaultMQPushConsumer orderTimeoutConsumer(RocketMqOrderTimeoutProperties properties,
                                                RocketMqOrderTimeoutConsumer listener)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        consumer.setNamesrvAddr(properties.getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setMaxReconsumeTimes(properties.getMaxReconsumeTimes());
        consumer.subscribe(properties.getTopic(), OrderTimeoutEvent.MESSAGE_TAG);
        consumer.registerMessageListener(listener);
        consumer.start();
        return consumer;
    }
}
