package com.example.bootserver.catalog.internal.application.query;

import java.util.Optional;

/** 商品详情查询缓存端口；缓存未命中或不可用都以空值回退数据库。 */
public interface ProductDetailsCache {

    Optional<ProductDetails> get(Long productId);

    void put(ProductDetails productDetails);
}
