package com.example.bootserver.order.internal.domain;

import java.math.BigDecimal;

/** 下单时的 SKU 单价快照和数量。 */
public record OrderLine(Long skuId, int quantity, BigDecimal unitPrice) {
    public OrderLine {
        if (skuId == null || skuId <= 0 || quantity < 1 || quantity > 999 || unitPrice == null
                || unitPrice.signum() < 0 || unitPrice.scale() > 2
                || unitPrice.setScale(2).precision() > 19) {
            throw new IllegalArgumentException("订单明细不合法");
        }
    }

    public BigDecimal amount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
