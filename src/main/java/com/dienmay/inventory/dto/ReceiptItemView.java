package com.dienmay.inventory.dto;


public record ReceiptItemView(Long productId, String sku, String productName, int quantity, java.math.BigDecimal unitCost, java.util.List<ReceiptSerialView> serials) { }
