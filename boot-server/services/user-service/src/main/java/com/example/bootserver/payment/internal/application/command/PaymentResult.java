package com.example.bootserver.payment.internal.application.command;

import com.example.bootserver.payment.internal.domain.PaymentStatus;

import java.math.BigDecimal;

/** 同步模拟支付成功后的回执。 */
public record PaymentResult(Long id, Long orderId, String payNo,
                            BigDecimal amount, PaymentStatus status) {
}
