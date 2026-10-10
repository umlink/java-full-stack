package com.example.bootserver.inventory.internal.infrastructure.redis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 注册库存 Redis 配置，具体预占存储按开关条件装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({InventoryRedisProperties.class, InventoryReconciliationProperties.class})
@EnableScheduling
public class InventoryRedisConfiguration {
}
