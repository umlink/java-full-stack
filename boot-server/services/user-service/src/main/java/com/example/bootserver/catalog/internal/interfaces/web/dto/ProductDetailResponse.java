package com.example.bootserver.catalog.internal.interfaces.web.dto;

import java.util.List;

/** 上架商品详情及其未删除的 SKU。 */
public record ProductDetailResponse(Long id, String name, String description, List<SkuResponse> skus) {
}
