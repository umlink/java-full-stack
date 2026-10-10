package com.example.bootserver.order.internal.interfaces.web.admin;

import java.util.List;

/** 后台全量订单分页响应。 */
public record AdminOrderPageResponse(long page, long size, long total,
                                     List<AdminOrderSummaryResponse> items) {
}
