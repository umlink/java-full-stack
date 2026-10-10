package com.example.bootserver.order.api;

import com.example.bootserver.inventory.api.InventoryReservationId;

import java.math.BigDecimal;
import java.util.List;

/** 支付所需的订单金额与已预扣明细快照。 */
public record PayableOrder(Long id, BigDecimal totalAmount,
                           InventoryReservationId reservationId, List<Item> items) {
    public PayableOrder {
        items = List.copyOf(items);
    }

    public record Item(Long skuId, Integer quantity) {
    }
}
