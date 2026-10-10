package com.example.bootserver.payment.internal.application.command;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** 生成仅用于外部追踪的唯一支付流水号，不承担订单支付幂等。 */
@Component
public class PaymentNumberGenerator {
    public String nextPayNo() {
        return "PAY" + UUID.randomUUID().toString().replace("-", "");
    }
}
