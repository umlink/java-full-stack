package com.example.bootserver.order.internal.interfaces.web;

import java.util.List;

/** 当前用户订单分页响应。 */
public record OrderPageResponse(long page, long size, long total, List<OrderSummaryResponse> items) {
}
