package com.example.bootserver.inventory.internal.application.query;

public interface StockQueryRepository {

    StockView findBySkuId(Long skuId);
}
