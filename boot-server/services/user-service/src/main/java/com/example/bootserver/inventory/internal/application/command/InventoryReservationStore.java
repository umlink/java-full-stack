package com.example.bootserver.inventory.internal.application.command;

import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.inventory.api.InventoryReservationResult;

/** 可重建的库存预占性能副本；实现不得取代数据库条件更新。 */
public interface InventoryReservationStore {

    InventoryReservationResult preReserve(InventoryReservationId reservationId, Long skuId,
                                          int quantity, long initialAvailable);

    void complete(InventoryReservationId reservationId, Long skuId, int quantity);

    void compensate(InventoryReservationId reservationId, Long skuId, int quantity);

    void settle(InventoryReservationId reservationId, Long skuId, int quantity);

    void release(InventoryReservationId reservationId, Long skuId, int quantity);

    void invalidate(Long skuId);

    /** 以数据库可售量核对 Redis 副本；实现必须保证差异修复是原子的。 */
    InventoryReconciliationResult reconcile(Long skuId, long databaseAvailable);
}
