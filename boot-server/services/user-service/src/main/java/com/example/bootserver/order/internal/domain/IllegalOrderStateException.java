package com.example.bootserver.order.internal.domain;

/** 订单状态不允许当前操作。 */
public class IllegalOrderStateException extends RuntimeException {
    public IllegalOrderStateException(String message) {
        super(message);
    }
}
