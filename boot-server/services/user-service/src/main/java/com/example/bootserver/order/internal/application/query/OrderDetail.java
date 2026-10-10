package com.example.bootserver.order.internal.application.query;

import com.example.bootserver.order.internal.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 当前用户可见的订单头与明细聚合查询结果。 */
public record OrderDetail(Long id, String orderNo, OrderStatus status, BigDecimal totalAmount,
                          LocalDateTime createTime, List<OrderItemView> items) {
    public OrderDetail {
        items = List.copyOf(items);
    }
}
