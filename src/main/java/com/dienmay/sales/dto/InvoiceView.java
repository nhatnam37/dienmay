package com.dienmay.sales.dto;

public record InvoiceView(Long id, String code, String checkoutToken, Long branchId, String branchName, Long cashierId, String cashierName, String customerName, String customerPhone, java.math.BigDecimal subtotal, java.math.BigDecimal discountAmount, java.math.BigDecimal totalAmount, com.dienmay.sales.entity.InvoiceStatus status, String note, java.time.LocalDateTime createdAt, java.util.List<InvoiceItemView> items, java.util.List<PaymentView> payments) { }
