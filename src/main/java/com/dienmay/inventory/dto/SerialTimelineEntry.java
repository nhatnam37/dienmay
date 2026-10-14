package com.dienmay.inventory.dto;

public record SerialTimelineEntry(java.time.LocalDateTime changedAt, com.dienmay.inventory.serial.SerialStatus fromStatus, com.dienmay.inventory.serial.SerialStatus toStatus, String fromBranch, String toBranch, String actorName, String refType, Long refId, String documentCode, String documentUrl, String note) { }
