package com.example.bootserver.order.internal.application.command;

/** 消息发布端口；实现负责把稳定 key、tag 和延时等级写入 RocketMQ 消息。 */
public interface OrderTimeoutPublisher {

    void publish(OrderTimeoutDispatchTask task) throws Exception;
}
