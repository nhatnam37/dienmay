package com.dienmay.inventory.dto;


public record ReceiptSummary(Long id, String code, String supplierName, Long branchId, String branchName, String createdByName, java.time.LocalDateTime createdAt) { }
