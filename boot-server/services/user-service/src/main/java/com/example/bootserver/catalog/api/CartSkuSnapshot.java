package com.example.bootserver.catalog.api;

import java.math.BigDecimal;

/** 购物车展示所需的商品最小快照。 */
public record CartSkuSnapshot(Long skuId, String skuCode, String productName, BigDecimal unitPrice) {
}
