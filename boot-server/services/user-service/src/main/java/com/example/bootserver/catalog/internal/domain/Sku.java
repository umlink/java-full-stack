package com.example.bootserver.catalog.internal.domain;

import java.math.BigDecimal;

/** SKU 写模型；价格不变式与乐观锁版本都属于商品领域。 */
public final class Sku {

    private final Long id;
    private final Long productId;
    private final String skuCode;
    private BigDecimal price;
    private final Integer version;

    private Sku(Long id, Long productId, String skuCode, BigDecimal price, Integer version) {
        this.id = id;
        this.productId = productId;
        this.skuCode = skuCode;
        this.version = version;
        changePrice(price);
    }

    public static Sku create(Long productId, String skuCode, BigDecimal price) {
        return new Sku(null, productId, skuCode, price, 0);
    }

    public static Sku restore(Long id, Long productId, String skuCode, BigDecimal price, Integer version) {
        return new Sku(id, productId, skuCode, price, version);
    }

    public void changePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() < 0) {
            throw new InvalidSkuPriceException();
        }
        this.price = newPrice;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getVersion() {
        return version;
    }
}
