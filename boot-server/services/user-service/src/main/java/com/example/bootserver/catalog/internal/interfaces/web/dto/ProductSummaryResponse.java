package com.example.bootserver.catalog.internal.interfaces.web.dto;

/** 列表中的商品公开字段。 */
public record ProductSummaryResponse(Long id, String name, String description) {
}
