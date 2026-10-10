package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.inventory.api.InventoryReservationId;

/** 集中定义订单到库存预占标识的稳定映射，并兼容没有幂等键的旧订单。 */
public final class OrderReservationIds {

    private static final String IDEMPOTENT_ORDER_NAMESPACE_PREFIX = "order-user:";
    private static final String LEGACY_ORDER_NAMESPACE_PREFIX = "legacy-order-user:";

    private OrderReservationIds() {
    }

    public static InventoryReservationId fromIdempotencyKey(Long userId, String idempotencyKey) {
        requirePositive(userId, "用户 ID");
        return InventoryReservationId.from(IDEMPOTENT_ORDER_NAMESPACE_PREFIX + userId, idempotencyKey);
    }

    public static InventoryReservationId fromSnapshot(Long userId, Long orderId, String idempotencyKey) {
        if (idempotencyKey != null) {
            return fromIdempotencyKey(userId, idempotencyKey);
        }
        requirePositive(userId, "用户 ID");
        requirePositive(orderId, "订单 ID");
        return InventoryReservationId.from(LEGACY_ORDER_NAMESPACE_PREFIX + userId, orderId.toString());
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
    }
}
