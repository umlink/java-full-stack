package com.example.bootserver.catalog.internal.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bootserver.catalog.api.CartSkuSnapshot;
import com.example.bootserver.catalog.api.OrderableSkuQuote;
import com.example.bootserver.catalog.internal.application.query.CatalogQueryRepository;
import com.example.bootserver.catalog.internal.application.query.ProductDetails;
import com.example.bootserver.catalog.internal.application.query.ProductPage;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 商品查询侧 MyBatis 适配器。 */
@Repository
public class MyBatisCatalogQueryRepository implements CatalogQueryRepository {

    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;

    public MyBatisCatalogQueryRepository(ProductMapper productMapper, SkuMapper skuMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
    }

    @Override
    public boolean existsSku(Long skuId) {
        return skuMapper.selectById(skuId) != null;
    }

    @Override
    public List<OrderableSkuQuote> findOrderableSkuQuotes(Set<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>().in(Sku::getId, skuIds));
        if (skus.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = skus.stream().map(Sku::getProductId).collect(Collectors.toSet());
        Set<Long> onSaleProductIds = productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .in(Product::getId, productIds)
                        .eq(Product::getStatus, com.example.bootserver.catalog.internal.domain.Product.STATUS_ON_SALE))
                .stream().map(Product::getId).collect(Collectors.toSet());
        return skus.stream().filter(sku -> onSaleProductIds.contains(sku.getProductId()))
                .map(sku -> new OrderableSkuQuote(sku.getId(), sku.getPrice())).toList();
    }

    @Override
    public List<CartSkuSnapshot> findCartSkuSnapshots(Set<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>().in(Sku::getId, skuIds));
        if (skus.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = skus.stream().map(Sku::getProductId).collect(Collectors.toSet());
        Map<Long, String> names = productMapper.selectList(
                        new LambdaQueryWrapper<Product>().in(Product::getId, productIds)).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        return skus.stream()
                .filter(sku -> names.containsKey(sku.getProductId()))
                .map(sku -> new CartSkuSnapshot(sku.getId(), sku.getSkuCode(),
                        names.get(sku.getProductId()), sku.getPrice()))
                .toList();
    }

    @Override
    public ProductPage findOnSaleProducts(long page, long size) {
        Page<Product> result = productMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, com.example.bootserver.catalog.internal.domain.Product.STATUS_ON_SALE)
                        .orderByAsc(Product::getId));
        List<ProductPage.ProductSummary> items = result.getRecords().stream()
                .map(product -> new ProductPage.ProductSummary(
                        product.getId(), product.getName(), product.getDescription()))
                .toList();
        return new ProductPage(result.getCurrent(), result.getSize(), result.getTotal(), items);
    }

    @Override
    public ProductDetails findOnSaleProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || !com.example.bootserver.catalog.internal.domain.Product.STATUS_ON_SALE
                .equals(product.getStatus())) {
            return null;
        }
        List<ProductDetails.SkuDetails> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getProductId, id)
                        .orderByAsc(Sku::getId))
                .stream()
                .map(sku -> new ProductDetails.SkuDetails(sku.getId(), sku.getSkuCode(), sku.getPrice()))
                .toList();
        return new ProductDetails(product.getId(), product.getName(), product.getDescription(), skus);
    }
}
