package com.example.bootserver.inventory.internal.interfaces.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

/** 管理员设置 SKU 的绝对总量，不接受客户端提交 locked 或 available。 */
public record UpdateStockRequest(@NotNull @Min(0) @JsonDeserialize(using = StrictStockTotalDeserializer.class) Long total) {
}
