package com.example.bootserver.catalog.internal.infrastructure.cache;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 商品缓存运行配置；开关与 TTL 都由外部配置源覆盖。 */
@Validated
@ConfigurationProperties(prefix = "app.catalog-cache")
public class CatalogCacheProperties {

    private boolean enabled;

    @NotNull
    private Duration productDetailTtl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getProductDetailTtl() {
        return productDetailTtl;
    }

    public void setProductDetailTtl(Duration productDetailTtl) {
        this.productDetailTtl = productDetailTtl;
    }

    @AssertTrue(message = "商品详情缓存 TTL 必须大于等于 1 秒")
    public boolean isProductDetailTtlValid() {
        return productDetailTtl != null && productDetailTtl.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
