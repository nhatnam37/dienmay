package com.dienmay.inventory.entity;

import jakarta.persistence.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import java.time.LocalDateTime;

@Entity
@Table(name = "goods_transfer_item")
public class GoodsTransferItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private GoodsTransfer transfer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "serial_item_id", nullable = true)
    private SerialItem serialItem;

    @Column(nullable = false)
    private int quantity;

    public Long getId() { return id; }
    public GoodsTransfer getTransfer() { return transfer; }
    public void setTransfer(GoodsTransfer transfer) { this.transfer = transfer; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public SerialItem getSerialItem() { return serialItem; }
    public void setSerialItem(SerialItem serialItem) { this.serialItem = serialItem; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
