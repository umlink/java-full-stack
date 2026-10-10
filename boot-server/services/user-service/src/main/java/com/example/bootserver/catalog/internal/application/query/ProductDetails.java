package com.example.bootserver.catalog.internal.application.query;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetails(Long id, String name, String description, List<SkuDetails> skus) {

    public record SkuDetails(Long id, String skuCode, BigDecimal price) {
    }
}
