package com.example.bootserver.catalog.api;

import java.math.BigDecimal;

/** 商品上下文对下单公开的最小只读快照，不暴露其持久化实体。 */
public record OrderableSkuQuote(Long skuId, BigDecimal unitPrice) {
}
