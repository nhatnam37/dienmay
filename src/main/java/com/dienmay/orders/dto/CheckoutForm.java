package com.dienmay.orders.dto;
import com.dienmay.orders.entity.*;
public class CheckoutForm {
    private String placeToken;private Long branchId;private DeliveryType deliveryType=DeliveryType.PICKUP;private OrderPaymentMethod paymentMethod=OrderPaymentMethod.PAY_AT_STORE;
    private String receiverName;private String receiverPhone;private String deliveryAddress;private String note;
    public String getPlaceToken(){return placeToken;}public void setPlaceToken(String v){placeToken=v;}
    public Long getBranchId(){return branchId;}public void setBranchId(Long v){branchId=v;}
    public DeliveryType getDeliveryType(){return deliveryType;}public void setDeliveryType(DeliveryType v){deliveryType=v;}
    public OrderPaymentMethod getPaymentMethod(){return paymentMethod;}public void setPaymentMethod(OrderPaymentMethod v){paymentMethod=v;}
    public String getReceiverName(){return receiverName;}public void setReceiverName(String v){receiverName=v;}
    public String getReceiverPhone(){return receiverPhone;}public void setReceiverPhone(String v){receiverPhone=v;}
    public String getDeliveryAddress(){return deliveryAddress;}public void setDeliveryAddress(String v){deliveryAddress=v;}
    public String getNote(){return note;}public void setNote(String v){note=v;}
}
