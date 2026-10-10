package com.example.bootserver.inventory.internal.application.command;

/** 库存对账指标协议，由调度适配器写入。 */
public final class InventoryReconciliationMetrics {

    public static final String OPERATIONS = "bootmall.inventory.reconciliation.operations";
    public static final String RESULT_TAG = "result";

    private InventoryReconciliationMetrics() {
    }
}
