package com.example.bootserver.order.internal.application.command;

/** 延时事件消费后的有限业务结果，用于稳定指标标签。 */
public enum OrderTimeoutHandlingResult {
    PROCESSED,
    IGNORED,
    DUPLICATE
}
