package com.example.bootserver.catalog.internal.infrastructure.cache;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册商品缓存配置绑定，具体适配器再按开关条件装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CatalogCacheConfiguration {
}
