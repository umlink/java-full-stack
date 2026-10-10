package com.example.bootserver.catalog.internal.application.query;

import com.example.bootserver.catalog.api.CartSkuSnapshot;
import com.example.bootserver.catalog.api.OrderableSkuQuote;

import java.util.List;
import java.util.Set;

/** 商品查询侧端口；查询投影可以针对页面和模块公开 API 独立优化。 */
public interface CatalogQueryRepository {

    boolean existsSku(Long skuId);

    List<OrderableSkuQuote> findOrderableSkuQuotes(Set<Long> skuIds);

    List<CartSkuSnapshot> findCartSkuSnapshots(Set<Long> skuIds);

    ProductPage findOnSaleProducts(long page, long size);

    ProductDetails findOnSaleProduct(Long id);
}
