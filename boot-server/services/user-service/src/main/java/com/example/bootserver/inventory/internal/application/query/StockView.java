package com.example.bootserver.inventory.internal.application.query;

public record StockView(Long skuId, long total, long locked, long available) {
}
