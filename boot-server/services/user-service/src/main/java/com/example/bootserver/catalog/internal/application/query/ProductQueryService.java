package com.example.bootserver.catalog.internal.application.query;

import com.example.bootserver.catalog.api.CartSkuSnapshot;
import com.example.bootserver.catalog.api.CatalogQueryApi;
import com.example.bootserver.catalog.api.OrderableSkuQuote;
import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** 商品只读查询规则：仅展示上架且未逻辑删除的商品和 SKU。 */
@Service
public class ProductQueryService implements CatalogQueryApi {

    private final CatalogQueryRepository queryRepository;

    public ProductQueryService(CatalogQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    /** 商品上下文提供给库存管理的只读契约：草稿或下架 SKU 仍可配置库存。 */
    @Override
    @Transactional(readOnly = true)
    public boolean hasSkuById(Long skuId) {
        return queryRepository.existsSku(skuId);
    }

    /** 一次批量读取商品和 SKU；仅向订单上下文返回当前可售的报价快照。 */
    @Override
    public List<OrderableSkuQuote> listOrderableSkuQuotes(Set<Long> skuIds) {
        return queryRepository.findOrderableSkuQuotes(skuIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartSkuSnapshot> listCartSkuSnapshots(Set<Long> skuIds) {
        return queryRepository.findCartSkuSnapshots(skuIds);
    }

    /** 按主键稳定排序后分页，避免不同请求的记录顺序漂移。 */
    @Transactional(readOnly = true)
    public ProductPage listProductsByPage(long page, long size) {
        return queryRepository.findOnSaleProducts(page, size);
    }

    /** 下架、草稿、逻辑删除和不存在的商品都以相同的不存在语义返回。 */
    @Transactional(readOnly = true)
    public ProductDetails getProductById(Long id) {
        ProductDetails product = queryRepository.findOnSaleProduct(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return product;
    }
}
