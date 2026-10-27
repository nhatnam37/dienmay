package com.dienmay.orders.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.dienmay.customer.entity.Customer;
import com.dienmay.organization.entity.Branch;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.context.ActorType;

@Entity @Table(name="order_item_serial")
public class OrderItemSerial {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_item_id",nullable=false) private OrderItem orderItem;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="serial_item_id",nullable=false) private SerialItem serialItem;
    @Column(name="released_at") private LocalDateTime releasedAt;

    public Long getId(){return id;}
    public OrderItem getOrderItem(){return orderItem;}
    public void setOrderItem(OrderItem value){orderItem=value;}
    public SerialItem getSerialItem(){return serialItem;}
    public void setSerialItem(SerialItem value){serialItem=value;}
    public LocalDateTime getReleasedAt(){return releasedAt;}
    public void setReleasedAt(LocalDateTime value){releasedAt=value;}
}
