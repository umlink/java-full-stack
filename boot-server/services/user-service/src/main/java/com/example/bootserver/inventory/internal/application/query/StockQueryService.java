package com.example.bootserver.inventory.internal.application.query;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockQueryService {

    private final StockQueryRepository repository;

    public StockQueryService(StockQueryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StockView getBySkuId(Long skuId) {
        StockView stock = repository.findBySkuId(skuId);
        if (stock == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "库存尚未配置");
        }
        return stock;
    }
}
