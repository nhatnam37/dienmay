package com.dienmay.inventory.repository;


public interface QuantityStockProjection {
    Long getBranchId();
    String getBranchName();
    Long getProductId();
    String getSku();
    String getProductName();
    String getCategoryName();
    long getQuantity();
    long getReservedQty();
}
