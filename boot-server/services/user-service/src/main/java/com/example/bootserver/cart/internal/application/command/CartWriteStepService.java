package com.example.bootserver.cart.internal.application.command;

import com.example.bootserver.cart.internal.domain.CartQuantity;

/** 购物车写步骤端口；存储模式决定步骤是否需要本地数据库事务。 */
public interface CartWriteStepService {

    int increase(Long userId, Long skuId, CartQuantity delta);

    void insert(Long userId, Long skuId, CartQuantity quantity);

    int replace(Long userId, Long itemId, CartQuantity quantity);

    int delete(Long userId, Long itemId);
}
