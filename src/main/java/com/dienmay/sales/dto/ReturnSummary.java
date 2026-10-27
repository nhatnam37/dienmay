package com.dienmay.sales.dto;
public record ReturnSummary(Long id,String code,Long invoiceId,String invoiceCode,Long branchId,String branchName,com.dienmay.sales.entity.ReturnType type,String reason,java.math.BigDecimal refundTotal,java.time.LocalDateTime createdAt) {  }
