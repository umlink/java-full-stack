package com.example.bootserver.cart.internal.infrastructure.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 注册 Redis 购物车配置绑定，存储适配器再按开关条件装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CartRedisProperties.class)
public class CartRedisConfiguration {
}
