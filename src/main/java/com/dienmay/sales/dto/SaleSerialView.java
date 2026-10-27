package com.dienmay.sales.dto;

public record SaleSerialView(String serialNo, Long productId, String sku, String productName, java.math.BigDecimal price, Long branchId, String branchName, com.dienmay.inventory.serial.SerialStatus status, boolean sellable, String reason) { }
