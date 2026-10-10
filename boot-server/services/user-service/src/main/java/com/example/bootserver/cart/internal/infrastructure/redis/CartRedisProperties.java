package com.example.bootserver.cart.internal.infrastructure.redis;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Redis 购物车运行配置；所有时长都允许从部署环境覆盖。 */
@Validated
@ConfigurationProperties(prefix = "app.cart.redis")
public class CartRedisProperties {

    private boolean enabled;

    @NotNull
    private Duration ttl;

    @NotNull
    private Duration migrationLockTtl;

    @NotNull
    private Duration migrationWaitTimeout;

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

    public Duration getMigrationLockTtl() {
        return migrationLockTtl;
    }

    public void setMigrationLockTtl(Duration migrationLockTtl) {
        this.migrationLockTtl = migrationLockTtl;
    }

    public Duration getMigrationWaitTimeout() {
        return migrationWaitTimeout;
    }

    public void setMigrationWaitTimeout(Duration migrationWaitTimeout) {
        this.migrationWaitTimeout = migrationWaitTimeout;
    }

    @AssertTrue(message = "Redis 购物车 TTL 与迁移时长必须大于等于 1 秒")
    public boolean areDurationsValid() {
        Duration minimum = Duration.ofSeconds(1);
        return ttl != null && ttl.compareTo(minimum) >= 0
                && migrationLockTtl != null && migrationLockTtl.compareTo(minimum) >= 0
                && migrationWaitTimeout != null && migrationWaitTimeout.compareTo(minimum) >= 0;
    }
}
