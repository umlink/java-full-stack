package com.example.bootserver.catalog.internal.application.command;

/** 商品写侧的缓存失效端口；适配器负责保证事务提交后再执行删除。 */
public interface CatalogCacheInvalidator {

    void invalidateProduct(Long productId);
}
