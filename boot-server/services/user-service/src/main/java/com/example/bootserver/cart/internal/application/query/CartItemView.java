package com.example.bootserver.cart.internal.application.query;

import java.math.BigDecimal;

/** 购物车接口需要的跨模块组合投影。 */
public record CartItemView(Long id, Long skuId, String skuCode, String productName,
                           BigDecimal unitPrice, int quantity) {
}
