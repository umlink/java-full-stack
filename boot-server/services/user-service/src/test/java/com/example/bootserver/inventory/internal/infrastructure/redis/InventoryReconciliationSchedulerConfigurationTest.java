package com.example.bootserver.inventory.internal.infrastructure.redis;

import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InventoryReconciliationSchedulerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(InventoryRedisConfiguration.class,
                    InventoryReconciliationScheduler.class)
            .withBean(InventoryReconciliationService.class,
                    () -> mock(InventoryReconciliationService.class))
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                    "app.inventory.redis.ttl=PT1M",
                    "app.inventory.reconciliation.interval=PT1H",
                    "app.inventory.reconciliation.batch-size=10");

    @Test
    void redisSwitchDisablesScheduler() {
        contextRunner.withPropertyValues(
                        "app.inventory.redis.enabled=false",
                        "app.inventory.reconciliation.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(InventoryReconciliationScheduler.class));
    }

    @Test
    void reconciliationSwitchDisablesScheduler() {
        contextRunner.withPropertyValues(
                        "app.inventory.redis.enabled=true",
                        "app.inventory.reconciliation.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(InventoryReconciliationScheduler.class));
    }

    @Test
    void bothSwitchesEnableScheduler() {
        contextRunner.withPropertyValues(
                        "app.inventory.redis.enabled=true",
                        "app.inventory.reconciliation.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(InventoryReconciliationScheduler.class));
    }
}
