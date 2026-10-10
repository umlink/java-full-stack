package com.example.bootserver.inventory.internal.infrastructure.redis;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 库存对账开关、调度周期和单批扫描上限。 */
@Validated
@ConfigurationProperties(prefix = "app.inventory.reconciliation")
public class InventoryReconciliationProperties {

    private boolean enabled;

    @NotNull
    private Duration interval;

    @Min(1)
    @Max(1000)
    private int batchSize;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getInterval() {
        return interval;
    }

    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @AssertTrue(message = "库存对账周期必须大于等于 1 秒")
    public boolean isIntervalValid() {
        return interval != null && interval.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
