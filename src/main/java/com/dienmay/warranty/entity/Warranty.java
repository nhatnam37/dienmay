package com.dienmay.warranty.entity;

import jakarta.persistence.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.catalog.entity.Product;
import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.sales.entity.InvoiceItem;
import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(name = "warranty")
public class Warranty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id", nullable = false)
    private InvoiceItem invoiceItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "serial_item_id", nullable = false)
    private SerialItem serialItem;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 20)
    private String customerPhone;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private WarrantyStatus status = WarrantyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public InvoiceItem getInvoiceItem() { return invoiceItem; }
    public void setInvoiceItem(InvoiceItem invoiceItem) { this.invoiceItem = invoiceItem; }
    public SerialItem getSerialItem() { return serialItem; }
    public void setSerialItem(SerialItem serialItem) { this.serialItem = serialItem; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public WarrantyStatus getStatus() { return status; }
    public void setStatus(WarrantyStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
