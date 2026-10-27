package com.dienmay.orders.entity;
public enum OrderStatus {
    PENDING_PAYMENT, PLACED, CONFIRMED, SHIPPING, READY_FOR_PICKUP, COMPLETED, CANCELLED, EXPIRED;
    public String getLabel(){return switch(this){case PENDING_PAYMENT->"Chờ thanh toán";case PLACED->"Đã đặt";case CONFIRMED->"Đã xác nhận";case SHIPPING->"Đang giao";case READY_FOR_PICKUP->"Sẵn sàng nhận";case COMPLETED->"Hoàn tất";case CANCELLED->"Đã hủy";case EXPIRED->"Hết hạn";};}
}
