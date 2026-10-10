package com.example.bootserver.inventory.internal.interfaces.web;

/** 库存读模型；available 由当前 total - locked 计算，不单独持久化。 */
public record StockResponse(Long skuId, long total, long locked, long available) {
}
