package com.example.bootserver.catalog.internal.application.query;

import java.util.List;

public record ProductPage(long page, long size, long total, List<ProductSummary> items) {

    public record ProductSummary(Long id, String name, String description) {
    }
}
