package com.dienmay.sales.dto;

public record InvoiceItemView(Long id, Long productId, String sku, String productName, String serialNo, int quantity, java.math.BigDecimal unitPrice, java.math.BigDecimal lineTotal) { }
