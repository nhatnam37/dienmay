package com.dienmay.inventory.dto;

public record TransferItemView(Long productId, String sku, String productName, String serialNo, int quantity) { }
