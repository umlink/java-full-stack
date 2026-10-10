package com.example.bootserver.order.internal.interfaces.web;

import com.example.bootserver.order.internal.domain.OrderStatus;

import java.math.BigDecimal;

/** 新建订单回执。 */
public record CreateOrderResponse(Long id, String orderNo, OrderStatus status, BigDecimal totalAmount) {
}
