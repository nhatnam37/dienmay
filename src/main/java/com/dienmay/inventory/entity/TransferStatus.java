package com.dienmay.inventory.entity;

public enum TransferStatus {
    DRAFT("Nháp"), SHIPPED("Đang chuyển"), RECEIVED("Đã nhận"), CANCELLED("Đã hủy");
    private final String label;
    TransferStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
