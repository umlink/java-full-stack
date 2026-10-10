package com.example.bootserver.order.internal.interfaces.web.admin;

import com.example.bootserver.order.internal.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台订单分页列表项。 */
public record AdminOrderSummaryResponse(Long id, String orderNo, Long userId, OrderStatus status,
                                        BigDecimal totalAmount, LocalDateTime createTime) {
}
