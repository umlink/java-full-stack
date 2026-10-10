package com.example.bootserver.order.internal.application.command;

/** 一次 Outbox 发送尝试所需的不可变消息快照。 */
public record OrderTimeoutDispatchTask(Long id, String eventId, String businessKey,
                                       Long orderId, String payload, int sendAttempts) {
}
