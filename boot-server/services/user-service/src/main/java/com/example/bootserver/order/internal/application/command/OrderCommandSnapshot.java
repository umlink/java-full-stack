package com.example.bootserver.order.internal.application.command;

import java.math.BigDecimal;
import java.util.List;

/** 状态命令完成后释放或结算库存所需的订单快照。 */
public record OrderCommandSnapshot(Long id, BigDecimal totalAmount, List<Item> items) {
    public OrderCommandSnapshot {
        items = List.copyOf(items);
    }

    public record Item(Long skuId, Integer quantity) {
    }
}
