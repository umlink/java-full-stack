package com.example.bootserver.cart.internal.interfaces.web;

import java.math.BigDecimal;

/** 购物车条目与当前 SKU 单价；不表示已锁价或已预留库存。 */
public record CartItemResponse(Long id, Long skuId, String skuCode, String productName,
                               BigDecimal unitPrice, int quantity) {
}
