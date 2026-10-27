package com.dienmay.sales.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="sales_return_item")
public class SalesReturnItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="return_id")
    private SalesReturn salesReturn;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="invoice_item_id")
    private InvoiceItem invoiceItem;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="serial_item_id")
    private com.dienmay.inventory.serial.SerialItem serialItem;
    @Column(nullable=false)
    private int quantity;
    @Column(name="refund_amount",nullable=false,precision=15,scale=2)
    private BigDecimal refundAmount;
    public Long getId(){return id;}
    public SalesReturn getSalesReturn(){return salesReturn;}
    public void setSalesReturn(SalesReturn value){this.salesReturn=value;}
    public InvoiceItem getInvoiceItem(){return invoiceItem;}
    public void setInvoiceItem(InvoiceItem value){this.invoiceItem=value;}
    public com.dienmay.inventory.serial.SerialItem getSerialItem(){return serialItem;}
    public void setSerialItem(com.dienmay.inventory.serial.SerialItem value){this.serialItem=value;}
    public int getQuantity(){return quantity;}
    public void setQuantity(int value){this.quantity=value;}
    public BigDecimal getRefundAmount(){return refundAmount;}
    public void setRefundAmount(BigDecimal value){this.refundAmount=value;}
}
