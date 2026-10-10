package com.example.bootserver.order.api;

/** 支付模块认领待支付订单时使用的公开命令契约。 */
public interface OrderPaymentApi {
    PayableOrder claimForPayment(Long userId, Long orderId);
}
