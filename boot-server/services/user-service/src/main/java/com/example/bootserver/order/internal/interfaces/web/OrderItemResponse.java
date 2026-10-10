package com.example.bootserver.order.internal.interfaces.web;

import java.math.BigDecimal;

/** 订单详情中的下单价格快照。 */
public record OrderItemResponse(Long skuId, Integer quantity, BigDecimal price) {
}
