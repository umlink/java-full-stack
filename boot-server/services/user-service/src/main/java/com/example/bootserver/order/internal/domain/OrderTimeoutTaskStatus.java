package com.example.bootserver.order.internal.domain;

/** 本地 Outbox 从待发送到消费终态的持久化状态。 */
public enum OrderTimeoutTaskStatus {
    PENDING,
    SENDING,
    SENT,
    CONSUMING,
    PROCESSED,
    IGNORED,
    DEAD;

    public boolean isConsumed() {
        return this == PROCESSED || this == IGNORED;
    }
}
