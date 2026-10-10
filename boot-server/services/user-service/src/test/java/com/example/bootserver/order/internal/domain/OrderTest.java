package com.example.bootserver.order.internal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void amountUsesUnitPriceSnapshotAndQuantity() {
        Order order = Order.create(3L, "123", new IdempotencyKey("key-123"),
                OrderRequestFingerprint.from(3L, List.of(new OrderSelection(11L, 2), new OrderSelection(12L, 3))), List.of(
                new OrderLine(11L, 2, new BigDecimal("29.90")),
                new OrderLine(12L, 3, new BigDecimal("9.90"))));

        assertThat(order.totalAmount()).isEqualByComparingTo("89.50");
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void invalidQuantityOrPriceCannotEnterOrder() {
        assertThatThrownBy(() -> new OrderLine(11L, 0, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(11L, 1, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderLine(11L, 1, new BigDecimal("100000000000000000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.create(3L, "123", new IdempotencyKey("key-123"),
                OrderRequestFingerprint.from(3L, List.of(new OrderSelection(11L, 1))), List.of(
                new OrderLine(11L, 1, BigDecimal.ONE), new OrderLine(11L, 1, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyCreatedOrderCanTransitionToPaid() {
        assertThat(OrderStatus.CREATED.pay()).isEqualTo(OrderStatus.PAID);
        assertThatThrownBy(OrderStatus.PAID::pay).isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(OrderStatus.CANCELLED::pay).isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void onlyCreatedOrderCanTransitionToCancelled() {
        assertThat(OrderStatus.CREATED.cancel()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(OrderStatus.PAID::cancel).isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(OrderStatus.CANCELLED::cancel).isInstanceOf(IllegalOrderStateException.class);
    }

    @Test
    void shippingAndConfirmationFollowPaidShippedDoneSequence() {
        assertThat(OrderStatus.PAID.ship()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(OrderStatus.SHIPPED.confirm()).isEqualTo(OrderStatus.DONE);
        assertThatThrownBy(OrderStatus.CREATED::ship).isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(OrderStatus.DONE::ship).isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(OrderStatus.PAID::confirm).isInstanceOf(IllegalOrderStateException.class);
        assertThatThrownBy(OrderStatus.DONE::confirm).isInstanceOf(IllegalOrderStateException.class);
    }
}
