package com.dienmay.warranty.entity;

public enum WarrantyTicketStatus {
    RECEIVED, IN_REPAIR, COMPLETED, REJECTED, RETURNED, CANCELLED;
    public boolean canTransitionTo(WarrantyTicketStatus target) {
        if (target == null) return false;
        return switch (this) {
            case RECEIVED -> target == IN_REPAIR || target == CANCELLED || target == REJECTED;
            case IN_REPAIR -> target == COMPLETED || target == REJECTED;
            case COMPLETED, REJECTED -> target == RETURNED;
            default -> false;
        };
    }
    public String getLabel() {
        return switch(this) {
            case RECEIVED -> "Đã tiếp nhận";
            case IN_REPAIR -> "Đang sửa";
            case COMPLETED -> "Đã sửa xong, chờ nhận";
            case REJECTED -> "Từ chối bảo hành";
            case RETURNED -> "Đã trả khách";
            case CANCELLED -> "Đã hủy";
        };
    }
    public boolean isOpen() { return this != RETURNED && this != CANCELLED; }
}
