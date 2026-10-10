package com.example.bootserver.inventory.internal.infrastructure.redis;

import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.inventory.api.InventoryReservationResult;
import com.example.bootserver.inventory.internal.application.command.InventoryReservationStore;
import com.example.bootserver.inventory.internal.application.command.InventoryReconciliationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Redis 预占关闭时沿用数据库条件更新，不保留第二份库存状态。 */
@Component
@ConditionalOnProperty(prefix = "app.inventory.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpInventoryReservationStore implements InventoryReservationStore {

    @Override
    public InventoryReservationResult preReserve(InventoryReservationId reservationId, Long skuId,
                                                 int quantity, long initialAvailable) {
        return InventoryReservationResult.BYPASSED;
    }

    @Override
    public void complete(InventoryReservationId reservationId, Long skuId, int quantity) {
    }

    @Override
    public void compensate(InventoryReservationId reservationId, Long skuId, int quantity) {
    }

    @Override
    public void settle(InventoryReservationId reservationId, Long skuId, int quantity) {
    }

    @Override
    public void release(InventoryReservationId reservationId, Long skuId, int quantity) {
    }

    @Override
    public void invalidate(Long skuId) {
    }

    @Override
    public InventoryReconciliationResult reconcile(Long skuId, long databaseAvailable) {
        return InventoryReconciliationResult.missing();
    }
}
