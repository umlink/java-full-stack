package com.example.bootserver.inventory.internal.application.command;

import com.example.bootserver.catalog.api.CatalogQueryApi;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import com.example.bootserver.inventory.api.InventoryCommandApi;
import com.example.bootserver.inventory.api.InventoryReservationId;
import com.example.bootserver.inventory.api.InventoryReservationResult;
import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockBelowLockedException;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 库存管理用例；商品侧只提供 SKU 存在性，库存表仅由本上下文写入。 */
@Service
public class StockService implements InventoryCommandApi {

    private final StockRepository repository;
    private final StockWriteStepService writeSteps;
    private final InventoryReservationStore reservationStore;
    private final CatalogQueryApi catalog;

    public StockService(StockRepository repository, StockWriteStepService writeSteps,
                        InventoryReservationStore reservationStore,
                        CatalogQueryApi catalog) {
        this.repository = repository;
        this.writeSteps = writeSteps;
        this.reservationStore = reservationStore;
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
                reservationStore.invalidate(skuId);
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
        reservationStore.invalidate(skuId);
        return repository.getBySkuId(skuId).orElseThrow();
    }

    /** 事务开始前尝试 Redis 预占；Redis 不可用时由后续数据库条件更新兜底。 */
    @Override
    public InventoryReservationResult preReserve(InventoryReservationId reservationId,
                                                 Long skuId, int quantity) {
        requireReservationCommand(reservationId, skuId, quantity);
        Stock stock = repository.getBySkuId(skuId).orElse(null);
        if (stock == null || stock.available() < quantity) {
            return InventoryReservationResult.INSUFFICIENT;
        }
        return reservationStore.preReserve(reservationId, skuId, quantity, stock.available());
    }

    /** 调用方订单事务内执行数据库条件更新，数据库是最终库存守护者。 */
    @Override
    public boolean persistReservation(InventoryReservationId reservationId, Long skuId, int quantity) {
        requireReservationCommand(reservationId, skuId, quantity);
        return repository.reserveIfAvailable(skuId, quantity) == 1;
    }

    @Override
    public void completeReservation(InventoryReservationId reservationId, Long skuId, int quantity) {
        requireReservationCommand(reservationId, skuId, quantity);
        reservationStore.complete(reservationId, skuId, quantity);
    }

    @Override
    public void compensateReservation(InventoryReservationId reservationId, Long skuId, int quantity) {
        requireReservationCommand(reservationId, skuId, quantity);
        reservationStore.compensate(reservationId, skuId, quantity);
    }

    /** 供支付事务调用；领域快照和数据库条件更新共同防止锁定量被重复实扣。 */
    @Override
    public void settleReserved(InventoryReservationId reservationId, Long skuId, int quantity) {
        requireReservationCommand(reservationId, skuId, quantity);
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
        afterCommit(() -> reservationStore.settle(reservationId, skuId, quantity));
    }

    /** 供取消事务调用；只释放预扣，不恢复或改变总库存。 */
    @Override
    public void releaseReserved(InventoryReservationId reservationId, Long skuId, int quantity) {
        requireReservationCommand(reservationId, skuId, quantity);
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
        afterCommit(() -> reservationStore.release(reservationId, skuId, quantity));
    }

    private void requireReservationCommand(InventoryReservationId reservationId, Long skuId, int quantity) {
        if (reservationId == null || skuId == null || skuId <= 0 || quantity <= 0) {
            throw new IllegalArgumentException("库存预占命令不合法");
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
