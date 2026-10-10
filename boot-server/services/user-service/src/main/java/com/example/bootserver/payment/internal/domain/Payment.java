package com.example.bootserver.payment.internal.domain;

import java.math.BigDecimal;

/** 成功支付单；金额必须直接使用订单快照。 */
public record Payment(Long orderId, String payNo, BigDecimal amount, PaymentStatus status) {
    public Payment {
        if (orderId == null || orderId <= 0 || payNo == null || payNo.isBlank()
                || amount == null || amount.signum() < 0 || amount.scale() > 2
                || amount.setScale(2).precision() > 19 || status == null) {
            throw new IllegalArgumentException("支付单不合法");
        }
    }

    public static Payment success(Long orderId, String payNo, BigDecimal amount) {
        return new Payment(orderId, payNo, amount, PaymentStatus.SUCCESS);
    }
}
