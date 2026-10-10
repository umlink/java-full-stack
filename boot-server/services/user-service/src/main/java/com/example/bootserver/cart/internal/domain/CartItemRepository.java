package com.example.bootserver.cart.internal.domain;

import java.util.List;

/** 购物车存储端口；具体适配器负责用原子操作守住并发数量不变式。 */
public interface CartItemRepository {

    int increase(Long userId, Long skuId, CartQuantity delta);

    void insert(Long userId, Long skuId, CartQuantity quantity);

    int replace(Long userId, Long itemId, CartQuantity quantity);

    int delete(Long userId, Long itemId);

    List<CartItem> listByUserId(Long userId);
}
