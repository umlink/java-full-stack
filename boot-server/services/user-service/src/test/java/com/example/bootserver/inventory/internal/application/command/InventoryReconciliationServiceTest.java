package com.example.bootserver.inventory.internal.application.command;

import com.example.bootserver.inventory.internal.domain.Stock;
import com.example.bootserver.inventory.internal.domain.StockRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReconciliationServiceTest {

    @Test
    void oneSkuFailureDoesNotBlockTheRemainingBatch() {
        StockRepository stocks = mock(StockRepository.class);
        InventoryReservationStore reservations = mock(InventoryReservationStore.class);
        Stock first = new Stock(11L, 8, 2);
        Stock second = new Stock(12L, 5, 1);
        when(stocks.listBySkuIdAfter(10L, 2)).thenReturn(List.of(first, second));
        when(reservations.reconcile(11L, 6)).thenThrow(new IllegalStateException("redis unavailable"));
        when(reservations.reconcile(12L, 4)).thenReturn(
                new InventoryReconciliationResult(InventoryReconciliationStatus.CONSISTENT, "4"));

        InventoryReconciliationBatch result = new InventoryReconciliationService(stocks, reservations)
                .reconcileAfter(10L, 2);

        assertThat(result).isEqualTo(new InventoryReconciliationBatch(12L, 2, 1, 0, 0, 1));
        verify(reservations).reconcile(11L, 6);
        verify(reservations).reconcile(12L, 4);
    }

    @Test
    void shortBatchWrapsCursorAndClassifiesMissingCopies() {
        StockRepository stocks = mock(StockRepository.class);
        InventoryReservationStore reservations = mock(InventoryReservationStore.class);
        when(stocks.listBySkuIdAfter(12L, 3)).thenReturn(List.of(new Stock(15L, 9, 2)));
        when(reservations.reconcile(15L, 7)).thenReturn(InventoryReconciliationResult.missing());

        InventoryReconciliationBatch result = new InventoryReconciliationService(stocks, reservations)
                .reconcileAfter(12L, 3);

        assertThat(result).isEqualTo(new InventoryReconciliationBatch(0L, 1, 0, 1, 0, 0));
    }
}
