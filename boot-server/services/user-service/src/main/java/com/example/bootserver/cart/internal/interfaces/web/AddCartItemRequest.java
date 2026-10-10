package com.example.bootserver.cart.internal.interfaces.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** quantity 是正整数增量；BigDecimal 加零小数位校验，避免 JSON 小数被取整。 */
public record AddCartItemRequest(
        @NotNull @Positive Long skuId,
        @NotNull @DecimalMin("1") @DecimalMax("999") @Digits(integer = 3, fraction = 0)
        BigDecimal quantity) {
}
