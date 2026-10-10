package com.example.bootserver.order.internal.application.query;

import com.example.bootserver.order.internal.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单列表使用的只读投影。 */
public record OrderSummary(Long id, String orderNo, OrderStatus status,
                           BigDecimal totalAmount, LocalDateTime createTime) {
}
