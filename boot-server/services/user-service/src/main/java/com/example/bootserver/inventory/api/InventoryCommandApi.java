package com.example.bootserver.inventory.api;

/** 库存模块向交易流程公开的原子库存命令。 */
public interface InventoryCommandApi {
    /** Redis 原子预占；性能副本不可用时返回 BYPASSED，由数据库最终确认。 */
    InventoryReservationResult preReserve(InventoryReservationId reservationId, Long skuId, int quantity);

    /** 在调用方本地事务中持久化锁定量，返回 false 表示库存不足。 */
    boolean persistReservation(InventoryReservationId reservationId, Long skuId, int quantity);

    /** 数据库事务提交后确认 Redis 预占。 */
    void completeReservation(InventoryReservationId reservationId, Long skuId, int quantity);

    /** 数据库事务失败或部分预占失败时幂等回补 Redis。 */
    void compensateReservation(InventoryReservationId reservationId, Long skuId, int quantity);

    /** 支付成功后把预扣库存转为已售库存。 */
    void settleReserved(InventoryReservationId reservationId, Long skuId, int quantity);

    /** 订单取消后释放预扣库存。 */
    void releaseReserved(InventoryReservationId reservationId, Long skuId, int quantity);
}
