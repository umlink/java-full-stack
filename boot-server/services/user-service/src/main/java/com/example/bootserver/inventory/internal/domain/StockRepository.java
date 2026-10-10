package com.example.bootserver.inventory.internal.domain;

import java.util.Optional;

/** 库存聚合的持久化契约；写入必须由基础设施层保证条件更新。 */
public interface StockRepository {

    Optional<Stock> getBySkuId(Long skuId);

    void insert(Stock stock);

    int updateTotalIfEnough(Long skuId, long total);

    /** 条件预扣在数据库执行，返回 0 表示未配置或当前可用量不足。 */
    int reserveIfAvailable(Long skuId, int quantity);

    /** 已锁定量足够时，把预扣同步转为总量与锁定量的实扣。 */
    int settleReserved(Long skuId, int quantity);

    /** 已锁定量足够时释放预扣，总量不变。 */
    int releaseReserved(Long skuId, int quantity);
}
