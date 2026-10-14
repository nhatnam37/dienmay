package com.dienmay.inventory.dto;


public record ReceiptDetail(ReceiptSummary receipt, String note, java.util.List<ReceiptItemView> items, java.math.BigDecimal total) { }
