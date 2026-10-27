package com.dienmay.orders.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.dienmay.customer.entity.Customer;
import com.dienmay.organization.entity.Branch;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.context.ActorType;

@Entity @Table(name="order_status_history")
public class OrderStatusHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private CustomerOrder order;
    @Enumerated(EnumType.STRING) @Column(name="from_status",length=20) private OrderStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name="to_status",nullable=false,length=20) private OrderStatus toStatus;
    @Enumerated(EnumType.STRING) @Column(name="actor_type",nullable=false,length=10) private ActorType actorType;
    @Column(name="actor_id") private Long actorId;
    @Column(name="note",length=255) private String note;
    @Column(name="changed_at",nullable=false) private LocalDateTime changedAt;

    public Long getId(){return id;}
    public CustomerOrder getOrder(){return order;}
    public void setOrder(CustomerOrder value){order=value;}
    public OrderStatus getFromStatus(){return fromStatus;}
    public void setFromStatus(OrderStatus value){fromStatus=value;}
    public OrderStatus getToStatus(){return toStatus;}
    public void setToStatus(OrderStatus value){toStatus=value;}
    public ActorType getActorType(){return actorType;}
    public void setActorType(ActorType value){actorType=value;}
    public Long getActorId(){return actorId;}
    public void setActorId(Long value){actorId=value;}
    public String getNote(){return note;}
    public void setNote(String value){note=value;}
    public LocalDateTime getChangedAt(){return changedAt;}
    public void setChangedAt(LocalDateTime value){changedAt=value;}
}
