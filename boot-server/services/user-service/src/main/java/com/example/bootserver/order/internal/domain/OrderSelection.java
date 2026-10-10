package com.example.bootserver.order.internal.domain;

/** 下单语义只含 SKU 和数量；当前价格不参与请求指纹。 */
public record OrderSelection(Long skuId, int quantity) {
    public OrderSelection {
        if (skuId == null || skuId <= 0 || quantity < 1 || quantity > 999) {
            throw new IllegalArgumentException("订单 SKU 或数量不合法");
        }
    }
}
