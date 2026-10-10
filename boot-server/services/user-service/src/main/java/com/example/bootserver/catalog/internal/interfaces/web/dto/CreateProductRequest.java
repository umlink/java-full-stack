package com.example.bootserver.catalog.internal.interfaces.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 创建商品的字段白名单；状态、版本、删除与审计字段均由服务端维护。 */
public record CreateProductRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 1024) String description,
        @NotEmpty @Valid List<CreateSkuRequest> skus) {
}
