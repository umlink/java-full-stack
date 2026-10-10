package com.example.bootserver.inventory.internal.application.command;

import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 首次插入发生唯一键竞争时，失败事务结束后才能用新事务重试条件更新。 */
@Service
public class StockWriteStepService {

    private final StockRepository repository;

    public StockWriteStepService(StockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void insert(Stock stock) {
        repository.insert(stock);
    }

    @Transactional
    public int updateTotalIfEnough(Long skuId, long total) {
        return repository.updateTotalIfEnough(skuId, total);
    }
}
