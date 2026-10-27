package com.dienmay.inventory.dto;

public record StocktakeSummary(Long id, String code, Long branchId, String branchName, com.dienmay.inventory.entity.StocktakeStatus status, String createdBy, java.time.LocalDateTime createdAt) {  }
