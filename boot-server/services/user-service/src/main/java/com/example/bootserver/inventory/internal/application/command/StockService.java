package com.example.bootserver.inventory.internal.application.command;

import com.example.bootserver.catalog.api.CatalogQueryApi;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockBelowLockedException;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 库存管理用例；商品侧只提供 SKU 存在性，库存表仅由本上下文写入。 */
@Service
public class StockService implements InventoryCommandApi {

    private final StockRepository repository;
    private final StockWriteStepService writeSteps;
    private final CatalogQueryApi catalog;

    public StockService(StockRepository repository, StockWriteStepService writeSteps,
                        CatalogQueryApi catalog) {
        this.repository = repository;
        this.writeSteps = writeSteps;
        this.catalog = catalog;
    }

    /** 设置绝对总量；先由聚合检查快照，再由 SQL 在写入时检查最新锁定量。 */
    public Stock setTotal(Long skuId, long total) {
        if (!catalog.hasSkuById(skuId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "SKU 不存在");
        }
        Stock existing = repository.getBySkuId(skuId).orElse(null);
        if (existing == null) {
            try {
                writeSteps.insert(Stock.create(skuId, total));
                return repository.getBySkuId(skuId).orElseThrow();
            } catch (DuplicateKeyException ignored) {
                // 同一 SKU 的另一请求先插入；独立事务回滚后按最新锁定量重试条件更新。
                existing = repository.getBySkuId(skuId).orElseThrow();
            }
        }
        try {
            existing.withTotal(total);
        } catch (StockBelowLockedException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage());
        }
        if (writeSteps.updateTotalIfEnough(skuId, total) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "库存锁定量已变化，请重新查询后再设置");
        }
        return repository.getBySkuId(skuId).orElseThrow();
    }

    /** 供订单用例在其事务内调用；库存上下文独占 t_stock 写入。 */
    @Override
    public boolean reserveIfAvailable(Long skuId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("预扣数量必须为正数");
        }
        return repository.reserveIfAvailable(skuId, quantity) == 1;
    }

    /** 供支付事务调用；领域快照和数据库条件更新共同防止锁定量被重复实扣。 */
    @Override
    public void settleReserved(Long skuId, int quantity) {
        Stock stock = repository.getBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "库存记录不存在"));
        try {
            stock.settle(quantity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage());
        }
        if (repository.settleReserved(skuId, quantity) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已锁定库存不足");
        }
    }

    /** 供取消事务调用；只释放预扣，不恢复或改变总库存。 */
    @Override
    public void releaseReserved(Long skuId, int quantity) {
        Stock stock = repository.getBySkuId(skuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "库存记录不存在"));
        try {
            stock.release(quantity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, exception.getMessage());
        }
        if (repository.releaseReserved(skuId, quantity) == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "已锁定库存不足");
        }
    }
}
