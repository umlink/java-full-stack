package com.example.bootserver.catalog.internal.interfaces.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 创建商品时允许写入的 SKU 编码与价格。 */
public record CreateSkuRequest(
        @NotBlank @Size(max = 64) String skuCode,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price) {
}
