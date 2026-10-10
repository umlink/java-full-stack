package com.example.bootserver.catalog.internal.interfaces.web.dto;

/** 上下架成功后的商品身份与状态。 */
public record ProductStatusResponse(Long id, String status) {
}
