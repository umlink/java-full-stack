package com.example.bootserver.order.internal.application.query;

import com.example.bootserver.common.error.BusinessException;
import com.example.bootserver.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前用户订单的查询用例；不存在和非本人订单不区分响应。 */
@Service
public class OrderQueryService {
    private final OrderQueryRepository orders;

    public OrderQueryService(OrderQueryRepository orders) {
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public OrderPage listOrders(Long userId, long page, long size) {
        return orders.listByUserId(userId, page, size);
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrder(Long userId, Long id) {
        return orders.getByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "订单不存在"));
    }

    @Transactional(readOnly = true)
    public AdminOrderPage listAllOrders(long page, long size) {
        return orders.listAll(page, size);
    }
}
