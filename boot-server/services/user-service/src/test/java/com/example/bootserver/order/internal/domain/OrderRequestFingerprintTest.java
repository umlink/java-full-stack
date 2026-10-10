package com.example.bootserver.order.internal.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderRequestFingerprintTest {

    @Test
    void canonicalizationIgnoresItemOrderButIncludesUserAndQuantity() {
        OrderRequestFingerprint first = OrderRequestFingerprint.from(7L, List.of(
                new OrderSelection(12L, 3), new OrderSelection(11L, 2)));
        assertThat(first.value()).matches("[0-9a-f]{64}");
        assertThat(OrderRequestFingerprint.from(7L, List.of(
                new OrderSelection(11L, 2), new OrderSelection(12L, 3)))).isEqualTo(first);
        assertThat(OrderRequestFingerprint.from(8L, List.of(
                new OrderSelection(11L, 2), new OrderSelection(12L, 3)))).isNotEqualTo(first);
        assertThat(OrderRequestFingerprint.from(7L, List.of(
                new OrderSelection(11L, 3), new OrderSelection(12L, 3)))).isNotEqualTo(first);
    }

    @Test
    void keyAndSelectionRejectMalformedInput() {
        assertThatThrownBy(() -> new IdempotencyKey(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("中文")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey("x".repeat(65))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderSelection(1L, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderRequestFingerprint.from(7L, List.of(
                new OrderSelection(11L, 1), new OrderSelection(11L, 2))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
