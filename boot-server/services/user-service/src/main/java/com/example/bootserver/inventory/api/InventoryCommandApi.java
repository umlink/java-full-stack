package com.example.bootserver.inventory.api;

/** 库存模块向交易流程公开的原子库存命令。 */
public interface InventoryCommandApi {
    /** 返回 false 表示库存未配置或当前可用量不足。 */
    boolean reserveIfAvailable(Long skuId, int quantity);

    /** 支付成功后把预扣库存转为已售库存。 */
    void settleReserved(Long skuId, int quantity);

    /** 订单取消后释放预扣库存。 */
    void releaseReserved(Long skuId, int quantity);
}
