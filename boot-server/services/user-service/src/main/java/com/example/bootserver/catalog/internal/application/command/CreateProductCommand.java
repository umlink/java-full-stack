package com.example.bootserver.catalog.internal.application.command;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductCommand(String name, String description, List<CreateSkuCommand> skus) {

    public record CreateSkuCommand(String skuCode, BigDecimal price) {
    }
}
