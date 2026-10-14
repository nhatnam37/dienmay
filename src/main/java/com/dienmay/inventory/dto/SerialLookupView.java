package com.dienmay.inventory.dto;

public record SerialLookupView(Long id, String serialNo, String sku, String productName, String branchName, com.dienmay.inventory.serial.SerialStatus status, Long receiptId, String receiptCode, java.util.List<SerialTimelineEntry> history) { }
