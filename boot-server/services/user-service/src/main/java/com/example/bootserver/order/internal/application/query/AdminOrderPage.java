package com.example.bootserver.order.internal.application.query;

import java.util.List;

/** 后台全量订单分页结果。 */
public record AdminOrderPage(long page, long size, long total, List<AdminOrderSummary> items) {
    public AdminOrderPage {
        items = List.copyOf(items);
    }
}
