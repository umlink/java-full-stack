package com.example.bootserver.inventory.api;

/** Redis 预占结果；数据库仍会对所有可继续结果执行条件更新。 */
public enum InventoryReservationResult {
    ACQUIRED,
    JOINED,
    ALREADY_RESERVED,
    BYPASSED,
    INSUFFICIENT,
    CONFLICT
}
