package com.dienmay.inventory.serial;


public enum SerialStatus {
    IN_STOCK, RESERVED, SOLD, IN_TRANSFER, IN_WARRANTY_REPAIR, DAMAGED;

    public boolean canTransitionTo(SerialStatus target) {
        if (target == null) { return false; }
        // Bảng chuyển đổi tập trung một nơi; không ngầm cho phép chuyển về chính nó.
        return switch (this) {
            case IN_STOCK -> target == RESERVED || target == SOLD || target == IN_TRANSFER || target == DAMAGED;
            case RESERVED -> target == IN_STOCK || target == SOLD;
            case IN_TRANSFER -> target == IN_STOCK;
            case SOLD -> target == IN_STOCK || target == IN_WARRANTY_REPAIR;
            case IN_WARRANTY_REPAIR -> target == SOLD;
            case DAMAGED -> false;
        };
    }
}
