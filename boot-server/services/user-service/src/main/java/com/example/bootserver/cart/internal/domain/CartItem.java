package com.example.bootserver.cart.internal.domain;

/** 购物车模块拥有的持久化条目，不包含商品模块展示字段。 */
public record CartItem(Long id, Long skuId, int quantity) {
}
