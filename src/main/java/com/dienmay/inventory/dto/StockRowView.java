package com.dienmay.inventory.dto;

import com.dienmay.inventory.serial.SerialStatus;
import java.util.Map;

public record StockRowView(Long branchId, String branchName, Long productId, String sku,
                           String productName, String categoryName, boolean tracksSerial,
                           long quantity, long reservedQty, Map<SerialStatus, Long> counts) {
    public long available() { return tracksSerial ? count(SerialStatus.IN_STOCK) : quantity - reservedQty; }
    public long count(SerialStatus status) { return counts.getOrDefault(status, 0L); }
}
