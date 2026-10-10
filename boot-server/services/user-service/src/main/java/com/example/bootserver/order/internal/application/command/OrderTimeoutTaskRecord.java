package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.order.internal.domain.OrderTimeoutTaskStatus;

/** 消费端核对消息身份与幂等终态所需的 Outbox 记录。 */
public record OrderTimeoutTaskRecord(String eventId, String businessKey, Long orderId,
                                     OrderTimeoutTaskStatus status) {
}
