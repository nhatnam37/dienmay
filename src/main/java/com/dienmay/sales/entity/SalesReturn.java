package com.dienmay.sales.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="sales_return")
public class SalesReturn {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_id")
    private Invoice invoice;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="branch_id")
    private com.dienmay.organization.entity.Branch branch;
    @Column(nullable=false,length=50)
    private String code;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private ReturnType type;
    @Column(nullable=false,length=255)
    private String reason;
    @Column(name="refund_total",nullable=false,precision=15,scale=2)
    private BigDecimal refundTotal;
    @Enumerated(EnumType.STRING) @Column(name="actor_type",nullable=false,length=10)
    private com.dienmay.inventory.context.ActorType actorType;
    @Column(name="actor_id")
    private Long actorId;
    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt;
    public Long getId(){return id;}
    public Invoice getInvoice(){return invoice;}
    public void setInvoice(Invoice value){this.invoice=value;}
    public com.dienmay.organization.entity.Branch getBranch(){return branch;}
    public void setBranch(com.dienmay.organization.entity.Branch value){this.branch=value;}
    public String getCode(){return code;}
    public void setCode(String value){this.code=value;}
    public ReturnType getType(){return type;}
    public void setType(ReturnType value){this.type=value;}
    public String getReason(){return reason;}
    public void setReason(String value){this.reason=value;}
    public BigDecimal getRefundTotal(){return refundTotal;}
    public void setRefundTotal(BigDecimal value){this.refundTotal=value;}
    public com.dienmay.inventory.context.ActorType getActorType(){return actorType;}
    public void setActorType(com.dienmay.inventory.context.ActorType value){this.actorType=value;}
    public Long getActorId(){return actorId;}
    public void setActorId(Long value){this.actorId=value;}
    public LocalDateTime getCreatedAt(){return createdAt;}
    public void setCreatedAt(LocalDateTime value){this.createdAt=value;}
}
