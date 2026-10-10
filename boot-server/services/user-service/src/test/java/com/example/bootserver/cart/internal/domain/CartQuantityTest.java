package com.example.bootserver.cart.internal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartQuantityTest {

    @Test
    void acceptsOnlyQuantitiesFromOneThroughNineHundredNinetyNine() {
        assertThat(new CartQuantity(1).value()).isEqualTo(1);
        assertThat(new CartQuantity(999).value()).isEqualTo(999);
        assertThatThrownBy(() -> new CartQuantity(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartQuantity(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartQuantity(1000)).isInstanceOf(IllegalArgumentException.class);
    }
}
