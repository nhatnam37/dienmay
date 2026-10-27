package com.dienmay.orders.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.dienmay.customer.entity.Customer;
import com.dienmay.organization.entity.Branch;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.context.ActorType;

@Entity @Table(name="order_payment")
public class OrderPayment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private CustomerOrder order;
    @Column(name="amount",nullable=false,precision=15,scale=2) private BigDecimal amount;
    @Column(name="method",nullable=false,length=15) private String method;
    @Column(name="status",nullable=false,length=10) private String status;
    @Column(name="paid_at",nullable=false) private LocalDateTime paidAt;
    @Column(name="refunded_at") private LocalDateTime refundedAt;
    @Column(name="refund_reason",length=255) private String refundReason;

    public Long getId(){return id;}
    public CustomerOrder getOrder(){return order;}
    public void setOrder(CustomerOrder value){order=value;}
    public BigDecimal getAmount(){return amount;}
    public void setAmount(BigDecimal value){amount=value;}
    public String getMethod(){return method;}
    public void setMethod(String value){method=value;}
    public String getStatus(){return status;}
    public void setStatus(String value){status=value;}
    public LocalDateTime getPaidAt(){return paidAt;}
    public void setPaidAt(LocalDateTime value){paidAt=value;}
    public LocalDateTime getRefundedAt(){return refundedAt;}
    public void setRefundedAt(LocalDateTime value){refundedAt=value;}
    public String getRefundReason(){return refundReason;}
    public void setRefundReason(String value){refundReason=value;}
}
