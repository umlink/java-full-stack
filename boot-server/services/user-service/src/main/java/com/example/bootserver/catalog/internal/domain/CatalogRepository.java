package com.example.bootserver.catalog.internal.domain;

/** 商品命令侧端口；基础设施层负责实体映射和乐观锁条件更新。 */
public interface CatalogRepository {

    Product insertProduct(Product product);

    Sku insertSku(Sku sku);

    Product findProductById(Long id);

    Sku findSkuById(Long id);

    Sku updateSkuPrice(Sku sku, Integer expectedVersion);

    boolean updateProductStatus(Product product);
}
