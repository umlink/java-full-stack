package com.example.bootserver.catalog.internal.interfaces.web.dto;

import java.util.List;

/** 商品分页结果；实体的逻辑删除和审计字段不进入传输契约。 */
public record ProductPageResponse(long page, long size, long total, List<ProductSummaryResponse> items) {
}
