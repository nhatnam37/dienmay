package com.dienmay.orders.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.dienmay.customer.entity.Customer;
import com.dienmay.organization.entity.Branch;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.context.ActorType;

@Entity @Table(name="orders")
public class CustomerOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="code",nullable=false,length=50,unique=true) private String code;
    @Column(name="place_token",nullable=false,length=36,unique=true) private String placeToken;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="customer_id",nullable=false) private Customer customer;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="branch_id",nullable=false) private Branch branch;
    @Enumerated(EnumType.STRING) @Column(name="status",nullable=false,length=20) private OrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name="delivery_type",nullable=false,length=10) private DeliveryType deliveryType;
    @Enumerated(EnumType.STRING) @Column(name="payment_method",nullable=false,length=15) private OrderPaymentMethod paymentMethod;
    @Column(name="receiver_name",nullable=false,length=100) private String receiverName;
    @Column(name="receiver_phone",nullable=false,length=10) private String receiverPhone;
    @Column(name="delivery_address",length=255) private String deliveryAddress;
    @Column(name="note",length=255) private String note;
    @Column(name="subtotal",nullable=false,precision=15,scale=2) private BigDecimal subtotal;
    @Column(name="total_amount",nullable=false,precision=15,scale=2) private BigDecimal totalAmount;
    @Column(name="expires_at") private LocalDateTime expiresAt;
    @Version @Column(name="version",nullable=false) private Long version;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="cancelled_reason",length=255) private String cancelledReason;

    public Long getId(){return id;}
    public String getCode(){return code;}
    public void setCode(String value){code=value;}
    public String getPlaceToken(){return placeToken;}
    public void setPlaceToken(String value){placeToken=value;}
    public String getRequestHash(){return requestHash;}
    public void setRequestHash(String value){requestHash=value;}
    public Customer getCustomer(){return customer;}
    public void setCustomer(Customer value){customer=value;}
    public Branch getBranch(){return branch;}
    public void setBranch(Branch value){branch=value;}
    public OrderStatus getStatus(){return status;}
    public void setStatus(OrderStatus value){status=value;}
    public DeliveryType getDeliveryType(){return deliveryType;}
    public void setDeliveryType(DeliveryType value){deliveryType=value;}
    public OrderPaymentMethod getPaymentMethod(){return paymentMethod;}
    public void setPaymentMethod(OrderPaymentMethod value){paymentMethod=value;}
    public String getReceiverName(){return receiverName;}
    public void setReceiverName(String value){receiverName=value;}
    public String getReceiverPhone(){return receiverPhone;}
    public void setReceiverPhone(String value){receiverPhone=value;}
    public String getDeliveryAddress(){return deliveryAddress;}
    public void setDeliveryAddress(String value){deliveryAddress=value;}
    public String getNote(){return note;}
    public void setNote(String value){note=value;}
    public BigDecimal getSubtotal(){return subtotal;}
    public void setSubtotal(BigDecimal value){subtotal=value;}
    public BigDecimal getTotalAmount(){return totalAmount;}
    public void setTotalAmount(BigDecimal value){totalAmount=value;}
    public LocalDateTime getExpiresAt(){return expiresAt;}
    public void setExpiresAt(LocalDateTime value){expiresAt=value;}
    public Long getVersion(){return version;}
    public void setVersion(Long value){version=value;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime value){createdAt=value;}
    public LocalDateTime getUpdatedAt(){return updatedAt;}
    public void setUpdatedAt(LocalDateTime value){updatedAt=value;}
    public String getCancelledReason(){return cancelledReason;}
    public void setCancelledReason(String value){cancelledReason=value;}
}
