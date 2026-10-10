package com.example.bootserver.inventory.internal.infrastructure;

import com.example.bootserver.inventory.internal.application.query.StockQueryRepository;
import com.example.bootserver.inventory.internal.application.query.StockView;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisStockQueryRepository implements StockQueryRepository {

    private final StockMapper mapper;

    public MyBatisStockQueryRepository(StockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public StockView findBySkuId(Long skuId) {
        StockEntity entity = mapper.selectById(skuId);
        if (entity == null) {
            return null;
        }
        long available = entity.getTotalCount() - entity.getLockedCount();
        return new StockView(entity.getSkuId(), entity.getTotalCount(), entity.getLockedCount(), available);
    }
}
