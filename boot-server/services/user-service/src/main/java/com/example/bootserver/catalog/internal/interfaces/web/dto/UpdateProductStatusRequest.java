package com.example.bootserver.catalog.internal.interfaces.web.dto;

import com.example.bootserver.catalog.internal.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 本卡只允许上架或下架；草稿状态由创建时的服务端默认值确定。 */
public record UpdateProductStatusRequest(
        @NotBlank @Pattern(regexp = Product.STATUS_ON_SALE + "|" + Product.STATUS_OFF_SALE,
                message = "状态只能为 ON_SALE 或 OFF_SALE") String status) {
}
