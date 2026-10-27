package com.dienmay.sales.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="payment_refund")
public class PaymentRefund {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="return_id")
    private SalesReturn salesReturn;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_id")
    private Payment payment;
    @Column(nullable=false,precision=15,scale=2)
    private BigDecimal amount;
    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt;
    public Long getId(){return id;}
    public SalesReturn getSalesReturn(){return salesReturn;}
    public void setSalesReturn(SalesReturn value){this.salesReturn=value;}
    public Payment getPayment(){return payment;}
    public void setPayment(Payment value){this.payment=value;}
    public BigDecimal getAmount(){return amount;}
    public void setAmount(BigDecimal value){this.amount=value;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime value){this.createdAt=value;}
}
