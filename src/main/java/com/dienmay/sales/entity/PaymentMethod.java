package com.dienmay.sales.entity;
public enum PaymentMethod {
    CASH, CARD, TRANSFER, ONLINE;
    public String getLabel(){return switch(this){case CASH->"Tiền mặt";case CARD->"Thẻ";case TRANSFER->"Chuyển khoản";case ONLINE->"Online";};}
}
