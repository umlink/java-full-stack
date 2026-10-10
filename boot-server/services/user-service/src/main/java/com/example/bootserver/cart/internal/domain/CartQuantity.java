package com.example.bootserver.cart.internal.domain;

/** 单个购物车条目的合法数量；加购增量与改量目标都使用同一范围。 */
public record CartQuantity(int value) {

    public static final int MAX = 999;

    public CartQuantity {
        if (value < 1 || value > MAX) {
            throw new IllegalArgumentException("购物车数量必须在 1 到 999 之间");
        }
    }
}
