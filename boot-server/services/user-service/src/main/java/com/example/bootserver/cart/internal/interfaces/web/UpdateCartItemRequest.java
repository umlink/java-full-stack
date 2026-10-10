package com.example.bootserver.cart.internal.interfaces.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** quantity 是修改后的绝对数量；删除必须调用 DELETE。 */
public record UpdateCartItemRequest(
        @NotNull @DecimalMin("1") @DecimalMax("999") @Digits(integer = 3, fraction = 0)
        BigDecimal quantity) {
}
