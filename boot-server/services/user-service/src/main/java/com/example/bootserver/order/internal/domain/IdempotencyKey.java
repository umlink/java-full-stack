package com.example.bootserver.order.internal.domain;

/** 用户作用域内的稳定请求键；不修剪空白，以免改变客户端所选身份。 */
public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || !value.matches("[\\x21-\\x7E]{1,64}")) {
            throw new IllegalArgumentException("幂等键须为 1 至 64 个 ASCII 可见字符");
        }
    }
}
