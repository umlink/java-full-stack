package com.example.bootserver.inventory.internal.infrastructure;

import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 仓储适配器负责持久化对象与纯 Java 库存聚合之间的转换。 */
@Repository
public class MyBatisStockRepository implements StockRepository {

    private final StockMapper mapper;

    public MyBatisStockRepository(StockMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Stock> getBySkuId(Long skuId) {
        return Optional.ofNullable(mapper.selectById(skuId)).map(this::toStock);
    }

    @Override
    public List<Stock> listBySkuIdAfter(long afterSkuId, int limit) {
        return mapper.selectBySkuIdAfter(afterSkuId, limit).stream().map(this::toStock).toList();
    }

    @Override
    public void insert(Stock stock) {
        StockEntity entity = new StockEntity();
        entity.setSkuId(stock.skuId());
        entity.setTotalCount(stock.total());
        entity.setLockedCount(stock.locked());
        mapper.insert(entity);
    }

    @Override
    public int updateTotalIfEnough(Long skuId, long total) {
        return mapper.updateTotalIfEnough(skuId, total);
    }

    @Override
    public int reserveIfAvailable(Long skuId, int quantity) {
        return mapper.reserveIfAvailable(skuId, quantity);
    }

    @Override
    public int settleReserved(Long skuId, int quantity) {
        return mapper.settleReserved(skuId, quantity);
    }

    @Override
    public int releaseReserved(Long skuId, int quantity) {
        return mapper.releaseReserved(skuId, quantity);
    }

    private Stock toStock(StockEntity entity) {
        return new Stock(entity.getSkuId(), entity.getTotalCount(), entity.getLockedCount());
    }
}
