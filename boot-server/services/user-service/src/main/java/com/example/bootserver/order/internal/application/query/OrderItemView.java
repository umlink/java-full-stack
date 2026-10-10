package com.example.bootserver.order.internal.application.query;

import java.math.BigDecimal;

/** 订单详情中的下单快照明细。 */
public record OrderItemView(Long skuId, Integer quantity, BigDecimal price) {
}
