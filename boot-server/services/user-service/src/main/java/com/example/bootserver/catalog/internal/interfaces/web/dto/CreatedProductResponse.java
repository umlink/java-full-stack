package com.example.bootserver.catalog.internal.interfaces.web.dto;

import java.util.List;

/** 创建结果返回 SKU 初始版本，供管理员后续按版本改价。 */
public record CreatedProductResponse(Long id, String status, List<CreatedSkuResponse> skus) {

    public record CreatedSkuResponse(Long id, String skuCode, Integer version) {
    }
}
