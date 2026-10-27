package com.dienmay.sales.entity;

import jakarta.persistence.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.sales.entity.InvoiceItem;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "invoice_item")
public class InvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "serial_item_id", nullable = true)
    private SerialItem serialItem;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotal;

    // NULL chỉ dành cho dòng lịch sử trước V15: không được suy từ catalog hiện tại.
    @Column(name="warranty_months",updatable=false)
    private Integer warrantyMonths;
    public Integer getWarrantyMonths(){return warrantyMonths;}
    public void setWarrantyMonths(Integer value){warrantyMonths=value;}

    @Column(nullable=false)
    private boolean returned;
    public boolean isReturned(){return returned;}
    public void setReturned(boolean value){returned=value;}

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public SerialItem getSerialItem() { return serialItem; }
    public void setSerialItem(SerialItem serialItem) { this.serialItem = serialItem; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
