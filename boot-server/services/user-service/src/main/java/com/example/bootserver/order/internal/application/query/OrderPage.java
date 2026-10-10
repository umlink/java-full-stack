package com.example.bootserver.order.internal.application.query;

import java.util.List;

/** 当前用户订单的分页查询结果。 */
public record OrderPage(long page, long size, long total, List<OrderSummary> items) {
    public OrderPage {
        items = List.copyOf(items);
    }
}
