package com.example.bootserver.inventory.internal.application.command;

/** 对账结果保留修复前 Redis 值，供差异日志定位。 */
public record InventoryReconciliationResult(InventoryReconciliationStatus status,
                                            String redisAvailable) {

    public static InventoryReconciliationResult missing() {
        return new InventoryReconciliationResult(InventoryReconciliationStatus.MISSING, null);
    }
}
