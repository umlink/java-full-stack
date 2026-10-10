package com.example.bootserver.order.internal.application.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderNumberGeneratorTest {
    @Test
    void consecutiveNumbersAreUnique() {
        OrderNumberGenerator generator = new OrderNumberGenerator(0);
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 10000; i++) {
            values.add(generator.nextOrderNo());
        }
        assertThat(values).hasSize(10000);
    }

    @Test
    void workerIdMustFitTenBits() {
        assertThatThrownBy(() -> new OrderNumberGenerator(1024))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
