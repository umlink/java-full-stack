package com.example.bootserver.inventory.internal.domain;

/** 一个 SKU 的库存聚合；可用量始终等于总量减已锁定量。 */
public record Stock(Long skuId, long total, long locked) {

    public Stock {
        if (skuId == null || skuId <= 0 || total < 0 || locked < 0) {
            throw new IllegalArgumentException("库存标识和数量不合法");
        }
        if (locked > total) {
            throw new StockBelowLockedException();
        }
    }

    /** 首次配置库存时没有预扣记录。 */
    public static Stock create(Long skuId, long total) {
        return new Stock(skuId, total, 0);
    }

    /** 状态改变只能经过聚合；数据库条件更新负责并发下的最终检查。 */
    public Stock withTotal(long newTotal) {
        return new Stock(skuId, newTotal, locked);
    }

    /** 把已锁定库存转为实扣，领域快照先守护数量，数据库条件更新处理并发。 */
    public Stock settle(int quantity) {
        if (quantity <= 0 || locked < quantity) {
            throw new IllegalArgumentException("已锁定库存不足");
        }
        return new Stock(skuId, total - quantity, locked - quantity);
    }

    /** 取消订单时释放预扣，总库存保持不变。 */
    public Stock release(int quantity) {
        if (quantity <= 0 || locked < quantity) {
            throw new IllegalArgumentException("已锁定库存不足");
        }
        return new Stock(skuId, total, locked - quantity);
    }

    public long available() {
        return total - locked;
    }
}
