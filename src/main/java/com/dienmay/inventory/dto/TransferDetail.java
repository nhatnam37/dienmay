package com.dienmay.inventory.dto;

public record TransferDetail(TransferSummary summary, String note, String createdBy, String shippedBy, java.time.LocalDateTime shippedAt, String receivedBy, java.time.LocalDateTime receivedAt, java.util.List<TransferItemView> items, boolean canShip, boolean canReceive, boolean canCancel) { }
