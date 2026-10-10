package com.example.bootserver.payment.internal.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {
    @Test
    void successUsesOrderSnapshotAmount() {
        Payment payment = Payment.success(1L, "PAY123", new BigDecimal("19.90"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.amount()).isEqualByComparingTo("19.90");
    }

    @Test
    void invalidIdentityNumberOrAmountIsRejected() {
        assertThatThrownBy(() -> Payment.success(null, "PAY123", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.success(1L, " ", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.success(1L, "PAY123", new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.success(1L, "PAY123", new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
