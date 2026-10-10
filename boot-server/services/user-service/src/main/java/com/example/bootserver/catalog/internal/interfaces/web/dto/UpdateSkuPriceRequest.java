package com.example.bootserver.catalog.internal.interfaces.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** 改价请求携带客户端上次获得的版本；版本不匹配时拒绝覆盖。 */
public record UpdateSkuPriceRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer version) {
}
