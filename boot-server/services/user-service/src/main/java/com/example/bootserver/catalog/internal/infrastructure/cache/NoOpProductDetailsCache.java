package com.example.bootserver.catalog.internal.infrastructure.cache;

import com.example.bootserver.catalog.internal.application.command.CatalogCacheInvalidator;
import com.example.bootserver.catalog.internal.application.query.ProductDetails;
import com.example.bootserver.catalog.internal.application.query.ProductDetailsCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 缓存关闭时保持应用端口可用，所有查询自然回退数据库。 */
@Component
@ConditionalOnProperty(prefix = "app.catalog-cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpProductDetailsCache implements ProductDetailsCache, CatalogCacheInvalidator {

    @Override
    public Optional<ProductDetails> get(Long productId) {
        return Optional.empty();
    }

    @Override
    public void put(ProductDetails productDetails) {
        // 缓存显式关闭，无需保存可重建副本。
    }

    @Override
    public void invalidateProduct(Long productId) {
        // 缓存显式关闭，不存在需要失效的副本。
    }
}
