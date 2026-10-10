package com.example.bootserver.inventory.internal.infrastructure.redis;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Redis 库存副本配置；开关与 TTL 均由部署环境覆盖。 */
@Validated
@ConfigurationProperties(prefix = "app.inventory.redis")
public class InventoryRedisProperties {

    private boolean enabled;

    @NotNull
    private Duration ttl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    @AssertTrue(message = "Redis 库存 TTL 必须大于等于 1 秒")
    public boolean isTtlValid() {
        return ttl != null && ttl.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
