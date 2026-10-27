package com.dienmay.orders.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.dienmay.customer.entity.Customer;
import com.dienmay.organization.entity.Branch;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.context.ActorType;

@Entity @Table(name="order_item")
public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="order_id",nullable=false) private CustomerOrder order;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="product_id",nullable=false) private Product product;
    @Column(name="quantity",nullable=false) private int quantity;
    @Column(name="unit_price",nullable=false,precision=15,scale=2) private BigDecimal unitPrice;
    @Column(name="line_total",nullable=false,precision=15,scale=2) private BigDecimal lineTotal;

    public Long getId(){return id;}
    public CustomerOrder getOrder(){return order;}
    public void setOrder(CustomerOrder value){order=value;}
    public Product getProduct(){return product;}
    public void setProduct(Product value){product=value;}
    public int getQuantity(){return quantity;}
    public void setQuantity(int value){quantity=value;}
    public BigDecimal getUnitPrice(){return unitPrice;}
    public void setUnitPrice(BigDecimal value){unitPrice=value;}
    public BigDecimal getLineTotal(){return lineTotal;}
    public void setLineTotal(BigDecimal value){lineTotal=value;}
}
