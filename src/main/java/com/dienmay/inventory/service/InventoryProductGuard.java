package com.dienmay.inventory.service;

import com.dienmay.catalog.service.ProductInventoryGuard;
import com.dienmay.inventory.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class InventoryProductGuard implements ProductInventoryGuard {
    private final SerialItemRepository serials;
    private final StockQuantityRepository stocks;
    private final GoodsReceiptItemRepository receiptItems;

    public InventoryProductGuard(SerialItemRepository serials, StockQuantityRepository stocks,
                                 GoodsReceiptItemRepository receiptItems) {
        this.serials = serials;
        this.stocks = stocks;
        this.receiptItems = receiptItems;
    }

    @Override
    public boolean hasInventoryData(Long productId) {
        return serials.existsByProductId(productId)
                || stocks.existsByProductIdAndQuantityGreaterThan(productId, 0)
                || receiptItems.existsByProductId(productId);
    }
}
