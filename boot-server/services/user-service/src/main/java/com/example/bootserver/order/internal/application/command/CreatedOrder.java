package com.example.bootserver.order.internal.application.command;

import com.example.bootserver.order.internal.domain.OrderStatus;

import java.math.BigDecimal;

/** 创建订单的最小结果；读取订单使用独立查询投影。 */
public record CreatedOrder(Long id, String orderNo, OrderStatus status, BigDecimal totalAmount) {
}
