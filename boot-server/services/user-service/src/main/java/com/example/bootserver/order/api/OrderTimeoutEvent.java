package com.example.bootserver.order.api;

import java.time.Instant;
import java.util.UUID;

/** RocketMQ 对外集成事件；版本与业务键共同构成长期可演进的消息契约。 */
public record OrderTimeoutEvent(String eventId, String businessKey, Long orderId,
                                int version, Instant createdAt) {

    public static final int CURRENT_VERSION = 1;
    public static final String MESSAGE_TAG = "order-timeout-v1";
    private static final String BUSINESS_KEY_PREFIX = "order-timeout:v1:";

    public OrderTimeoutEvent {
        if (eventId == null || businessKey == null || orderId == null || orderId <= 0 || createdAt == null) {
            throw new IllegalArgumentException("订单超时事件字段不完整");
        }
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("订单超时事件 ID 不合法", exception);
        }
        if (version != CURRENT_VERSION || !businessKey.equals(businessKey(orderId))) {
            throw new IllegalArgumentException("订单超时事件版本或业务键不合法");
        }
    }

    public static OrderTimeoutEvent create(Long orderId, Instant createdAt) {
        return new OrderTimeoutEvent(UUID.randomUUID().toString(), businessKey(orderId),
                orderId, CURRENT_VERSION, createdAt);
    }

    public static String businessKey(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单 ID 必须为正数");
        }
        return BUSINESS_KEY_PREFIX + orderId;
    }
}
