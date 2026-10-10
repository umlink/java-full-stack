package com.example.bootserver.inventory.internal.application.command;

/** 一次有界库存对账的游标与分类计数。 */
public record InventoryReconciliationBatch(long nextCursor, int checked, int consistent,
                                           int missing, int repaired, int failed) {
}
