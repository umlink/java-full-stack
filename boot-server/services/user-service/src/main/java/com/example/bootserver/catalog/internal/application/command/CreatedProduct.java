package com.example.bootserver.catalog.internal.application.command;

import java.util.List;

public record CreatedProduct(Long id, String status, List<CreatedSku> skus) {

    public record CreatedSku(Long id, String skuCode, Integer version) {
    }
}
