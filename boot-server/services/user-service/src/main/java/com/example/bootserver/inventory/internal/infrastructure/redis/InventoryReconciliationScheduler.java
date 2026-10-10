package com.example.bootserver.inventory.internal.infrastructure.redis;

import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationBatch;
import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationMetrics;
import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 单实例轻量调度器；每次只处理一个配置批次，并用游标逐轮回绕。 */
@Component
@ConditionalOnProperty(name = {"app.inventory.redis.enabled", "app.inventory.reconciliation.enabled"},
        havingValue = "true")
public class InventoryReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationScheduler.class);

    private final InventoryReconciliationService service;
    private final InventoryReconciliationProperties properties;
    private final MeterRegistry meters;
    private long cursor;

    public InventoryReconciliationScheduler(InventoryReconciliationService service,
                                            InventoryReconciliationProperties properties,
                                            MeterRegistry meters) {
        this.service = service;
        this.properties = properties;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${app.inventory.reconciliation.interval}",
            initialDelayString = "${app.inventory.reconciliation.interval}")
    public synchronized void reconcileInventories() {
        InventoryReconciliationBatch batch = service.reconcileAfter(cursor, properties.getBatchSize());
        cursor = batch.nextCursor();
        increment("consistent", batch.consistent());
        increment("missing", batch.missing());
        increment("repaired", batch.repaired());
        increment("failed", batch.failed());
        if (batch.repaired() > 0 || batch.failed() > 0) {
            log.info("库存对账批次完成，checked={}, consistent={}, missing={}, repaired={}, failed={}, nextCursor={}",
                    batch.checked(), batch.consistent(), batch.missing(), batch.repaired(),
                    batch.failed(), batch.nextCursor());
        }
    }

    private void increment(String result, int amount) {
        if (amount <= 0) {
            return;
        }
        Counter.builder(InventoryReconciliationMetrics.OPERATIONS)
                .tag(InventoryReconciliationMetrics.RESULT_TAG, result)
                .register(meters)
                .increment(amount);
    }
}
