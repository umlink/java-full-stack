package com.example.bootserver.order.internal.domain;

import java.math.BigDecimal;

/** 按幂等键读取的原单投影；重放沿用历史状态和金额，而非重新报价。 */
public record ExistingOrder(Long id, String orderNo, OrderStatus status, BigDecimal totalAmount,
                            OrderRequestFingerprint fingerprint) {
}
