package com.example.bootserver.order.internal.interfaces.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 下单请求；用户身份、价格和金额不接受客户端输入。 */
public record CreateOrderRequest(
        @NotNull @Pattern(regexp = "[\\x21-\\x7E]{1,64}", message = "幂等键须为 1 至 64 个 ASCII 可见字符")
        String idempotencyKey,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid CreateOrderItemRequest> items) {
}
