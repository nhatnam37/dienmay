package com.dienmay.inventory.dto;

public record TransferSummary(Long id, String code, Long fromBranchId, String fromBranchName, Long toBranchId, String toBranchName, com.dienmay.inventory.entity.TransferStatus status, java.time.LocalDateTime createdAt) { }
