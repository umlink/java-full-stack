package com.example.bootserver.inventory.internal.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    void availableIsDerivedFromTotalAndLocked() {
        Stock stock = new Stock(1L, 10, 4);

        assertThat(stock.available()).isEqualTo(6);
        assertThat(stock.withTotal(8).available()).isEqualTo(4);
        assertThat(stock.locked()).isEqualTo(4);
    }

    @Test
    void totalCannotFallBelowLockedAndCountsCannotBeNegative() {
        Stock stock = new Stock(1L, 10, 7);

        assertThatThrownBy(() -> stock.withTotal(6)).isInstanceOf(StockBelowLockedException.class);
        assertThatThrownBy(() -> Stock.create(1L, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stock(1L, 10, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settlementReducesTotalAndLockedTogether() {
        Stock settled = new Stock(1L, 10, 4).settle(3);

        assertThat(settled.total()).isEqualTo(7);
        assertThat(settled.locked()).isEqualTo(1);
        assertThatThrownBy(() -> new Stock(1L, 10, 2).settle(3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseOnlyReducesLockedCount() {
        Stock released = new Stock(1L, 10, 4).release(3);

        assertThat(released.total()).isEqualTo(10);
        assertThat(released.locked()).isEqualTo(1);
        assertThatThrownBy(() -> new Stock(1L, 10, 2).release(3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
