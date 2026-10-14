package com.dienmay.inventory.service;

import com.dienmay.inventory.repository.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class InventoryProductGuardTest {
    private final SerialItemRepository serials = mock(SerialItemRepository.class);
    private final StockQuantityRepository stocks = mock(StockQuantityRepository.class);
    private final GoodsReceiptItemRepository receiptItems = mock(GoodsReceiptItemRepository.class);
    private final InventoryProductGuard guard = new InventoryProductGuard(serials, stocks, receiptItems);

    @Test
    void hasInventoryData_existingSerial_returnsTrue() {
        when(serials.existsByProductId(1L)).thenReturn(true);
        assertThat(guard.hasInventoryData(1L)).isTrue();
    }

    @Test
    void hasInventoryData_positiveStock_returnsTrue() {
        when(stocks.existsByProductIdAndQuantityGreaterThan(1L, 0)).thenReturn(true);
        assertThat(guard.hasInventoryData(1L)).isTrue();
    }

    @Test
    void hasInventoryData_receiptItemEvenWithoutStock_returnsTrue() {
        when(receiptItems.existsByProductId(1L)).thenReturn(true);
        assertThat(guard.hasInventoryData(1L)).isTrue();
    }

    @Test
    void hasInventoryData_noSerialPositiveStockOrReceipt_returnsFalse() {
        assertThat(guard.hasInventoryData(1L)).isFalse();
        verify(stocks).existsByProductIdAndQuantityGreaterThan(1L, 0);
    }
}
