package com.example.bootserver.catalog.internal.infrastructure.persistence;

import com.example.bootserver.catalog.internal.domain.CatalogRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/** 商品命令侧 MyBatis 适配器。 */
@Repository
public class MyBatisCatalogRepository implements CatalogRepository {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;

    public MyBatisCatalogRepository(ProductMapper productMapper, SkuMapper skuMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
    }

    @Override
    public com.example.bootserver.catalog.internal.domain.Product insertProduct(
            com.example.bootserver.catalog.internal.domain.Product product) {
        Product entity = new Product();
        entity.setName(product.name());
        entity.setDescription(product.description());
        entity.setStatus(product.status());
        productMapper.insert(entity);
        return new com.example.bootserver.catalog.internal.domain.Product(
                entity.getId(), entity.getName(), entity.getDescription(), entity.getStatus());
    }

    @Override
    public com.example.bootserver.catalog.internal.domain.Sku insertSku(
            com.example.bootserver.catalog.internal.domain.Sku sku) {
        Sku entity = new Sku();
        entity.setProductId(sku.getProductId());
        entity.setSkuCode(sku.getSkuCode());
        entity.setPrice(sku.getPrice());
        entity.setVersion(sku.getVersion());
        skuMapper.insert(entity);
        return toDomain(entity);
    }

    @Override
    public com.example.bootserver.catalog.internal.domain.Product findProductById(Long id) {
        Product entity = productMapper.selectById(id);
        return entity == null ? null : new com.example.bootserver.catalog.internal.domain.Product(
                entity.getId(), entity.getName(), entity.getDescription(), entity.getStatus());
    }

    @Override
    public com.example.bootserver.catalog.internal.domain.Sku findSkuById(Long id) {
        Sku entity = skuMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public com.example.bootserver.catalog.internal.domain.Sku updateSkuPrice(
            com.example.bootserver.catalog.internal.domain.Sku sku, Integer expectedVersion) {
        Sku update = new Sku();
        update.setId(sku.getId());
        update.setPrice(sku.getPrice());
        update.setVersion(expectedVersion);
        update.setUpdateTime(LocalDateTime.now());
        if (skuMapper.updateById(update) == 0) {
            return null;
        }
        return com.example.bootserver.catalog.internal.domain.Sku.restore(
                sku.getId(), sku.getProductId(), sku.getSkuCode(), sku.getPrice(), update.getVersion());
    }

    @Override
    public boolean updateProductStatus(com.example.bootserver.catalog.internal.domain.Product product) {
        Product update = new Product();
        update.setId(product.id());
        update.setStatus(product.status());
        update.setUpdateTime(LocalDateTime.now());
        return productMapper.updateById(update) > 0;
    }

    private com.example.bootserver.catalog.internal.domain.Sku toDomain(Sku entity) {
        return com.example.bootserver.catalog.internal.domain.Sku.restore(
                entity.getId(), entity.getProductId(), entity.getSkuCode(), entity.getPrice(), entity.getVersion());
    }
}
