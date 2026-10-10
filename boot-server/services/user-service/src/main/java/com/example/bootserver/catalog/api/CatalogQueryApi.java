package com.example.bootserver.catalog.api;

import java.util.List;
import java.util.Set;

/** 商品模块公开的只读协作契约。 */
public interface CatalogQueryApi {
    /** 草稿或下架 SKU 仍存在，可由库存模块配置库存。 */
    boolean hasSkuById(Long skuId);

    /** 只返回未删除且所属商品已上架的当前报价。 */
    List<OrderableSkuQuote> listOrderableSkuQuotes(Set<Long> skuIds);

    /** 为购物车返回当前展示信息；不表达锁价或库存承诺。 */
    List<CartSkuSnapshot> listCartSkuSnapshots(Set<Long> skuIds);
}
