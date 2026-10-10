package com.example.bootserver.inventory.internal.application.command;

/** 单个 SKU 的 Redis 库存副本对账结果。 */
public enum InventoryReconciliationStatus {
    CONSISTENT,
    MISSING,
    REPAIRED
}
