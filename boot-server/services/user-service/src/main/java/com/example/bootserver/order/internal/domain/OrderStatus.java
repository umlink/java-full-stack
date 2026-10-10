package com.example.bootserver.order.internal.domain;

/** 订单生命周期机器值；迁移规则由状态类型集中守护。 */
public enum OrderStatus {
    CREATED, PAID, SHIPPED, DONE, CANCELLED;

    /** 模拟支付只允许从待支付状态进入已支付。 */
    public OrderStatus pay() {
        if (this != CREATED) {
            throw new IllegalOrderStateException("当前订单状态不允许支付");
        }
        return PAID;
    }

    /** 只有待支付订单可由用户主动取消。 */
    public OrderStatus cancel() {
        if (this != CREATED) {
            throw new IllegalOrderStateException("当前订单状态不允许取消");
        }
        return CANCELLED;
    }

    /** 管理员只能发货已支付订单。 */
    public OrderStatus ship() {
        if (this != PAID) {
            throw new IllegalOrderStateException("当前订单状态不允许发货");
        }
        return SHIPPED;
    }

    /** 用户只能确认已发货订单。 */
    public OrderStatus confirm() {
        if (this != SHIPPED) {
            throw new IllegalOrderStateException("当前订单状态不允许确认收货");
        }
        return DONE;
    }
}
