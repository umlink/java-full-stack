package com.example.bootserver.order.internal.application.command;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 单实例雪花式订单号：41 位毫秒、10 位 worker、12 位毫秒内序列。 */
@Component
public class OrderNumberGenerator {
    private static final long EPOCH_MILLIS = 1704067200000L;
    private static final long MAX_SEQUENCE = 4095L;
    private final long workerId;
    private long lastMillis = -1;
    private long sequence;

    public OrderNumberGenerator(@Value("${app.order.worker-id}") int workerId) {
        if (workerId < 0 || workerId > 1023) {
            throw new IllegalArgumentException("订单 worker ID 必须在 0 到 1023 之间");
        }
        this.workerId = workerId;
    }

    /** 时钟回拨直接失败，不能悄悄产出可能重复的订单号。 */
    public synchronized String nextOrderNo() {
        long millis = System.currentTimeMillis();
        if (millis < lastMillis || millis < EPOCH_MILLIS || millis - EPOCH_MILLIS >= (1L << 41)) {
            throw new IllegalStateException("系统时钟回拨，无法生成订单号");
        }
        if (millis == lastMillis) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                do {
                    millis = System.currentTimeMillis();
                    if (millis < lastMillis) {
                        throw new IllegalStateException("系统时钟回拨，无法生成订单号");
                    }
                } while (millis <= lastMillis);
            }
        } else {
            sequence = 0;
        }
        lastMillis = millis;
        return Long.toString(((millis - EPOCH_MILLIS) << 22) | (workerId << 12) | sequence);
    }
}
