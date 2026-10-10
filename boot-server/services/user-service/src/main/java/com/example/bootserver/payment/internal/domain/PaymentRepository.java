package com.example.bootserver.payment.internal.domain;

/** 支付单持久化端口；订单唯一约束由数据库最终守护。 */
public interface PaymentRepository {
    Long insert(Payment payment);
}
