package com.dienmay.inventory.repository;


public interface SerialStockProjection {
    Long getBranchId();
    String getBranchName();
    Long getProductId();
    String getSku();
    String getProductName();
    String getCategoryName();
    com.dienmay.inventory.serial.SerialStatus getStatus();
    long getQuantity();
}
