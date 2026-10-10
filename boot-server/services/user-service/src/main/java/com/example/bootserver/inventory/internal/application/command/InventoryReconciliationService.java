package com.example.bootserver.inventory.internal.application.command;

import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 读取数据库权威库存并逐项核对可重建的 Redis 性能副本。 */
@Service
public class InventoryReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationService.class);

    private final StockRepository stocks;
    private final InventoryReservationStore reservations;

    public InventoryReconciliationService(StockRepository stocks,
                                          InventoryReservationStore reservations) {
        this.stocks = stocks;
        this.reservations = reservations;
    }

    public InventoryReconciliationBatch reconcileAfter(long afterSkuId, int batchSize) {
        if (afterSkuId < 0 || batchSize <= 0) {
            throw new IllegalArgumentException("库存对账游标和批次必须合法");
        }
        List<Stock> batch = stocks.listBySkuIdAfter(afterSkuId, batchSize);
        int consistent = 0;
        int missing = 0;
        int repaired = 0;
        int failed = 0;
        for (Stock stock : batch) {
            long databaseAvailable = stock.available();
            try {
                InventoryReconciliationResult result = reservations.reconcile(
                        stock.skuId(), databaseAvailable);
                switch (result.status()) {
                    case CONSISTENT -> consistent++;
                    case MISSING -> missing++;
                    case REPAIRED -> {
                        repaired++;
                        log.warn("库存对账已失效差异副本，skuId={}, databaseAvailable={}, redisAvailable={}",
                                stock.skuId(), databaseAvailable, result.redisAvailable());
                    }
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("库存对账失败，skuId={}, databaseAvailable={}",
                        stock.skuId(), databaseAvailable, exception);
            }
        }
        long nextCursor = batch.size() < batchSize
                ? 0L : batch.get(batch.size() - 1).skuId();
        return new InventoryReconciliationBatch(nextCursor, batch.size(), consistent,
                missing, repaired, failed);
    }
}
