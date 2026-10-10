package com.example.bootserver.catalog.internal.application.command;

import java.math.BigDecimal;

public record SkuPrice(Long id, BigDecimal price, Integer version) {
}
