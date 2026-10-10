package com.example.bootserver.order.internal.interfaces.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 每行只由客户端指定 SKU 和数量。 */
public record CreateOrderItemRequest(@NotNull @Min(1) Long skuId,
                                     @NotNull @Min(1) @Max(999) Integer quantity) {
}
