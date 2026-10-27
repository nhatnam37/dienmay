package com.dienmay.inventory.dto;

public record StocktakeSnapshotRow(Long productId, String sku, String productName, String serialNo, com.dienmay.inventory.serial.SerialStatus serialStatus, int quantity, int reservedQty, Long version) { 
    public String key() { return serialNo == null ? "Q:" + productId : "S:" + serialNo; }
    public boolean inScope() { return serialNo == null || serialStatus == com.dienmay.inventory.serial.SerialStatus.IN_STOCK; }
 }
