package com.example.bootserver.order.internal.application.query;

import java.util.Optional;

/** 订单只读查询端口；本人查询显式携带用户标识，后台查询返回所有者投影。 */
public interface OrderQueryRepository {
    OrderPage listByUserId(Long userId, long page, long size);

    Optional<OrderDetail> getByIdAndUserId(Long id, Long userId);

    AdminOrderPage listAll(long page, long size);
}
