package com.dienmay.inventory.entity;

import jakarta.persistence.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.catalog.entity.Product;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocktake_line")
public class StocktakeLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "serial_no", length = 30)
    private String serialNo;

    @Column(name = "counted_qty")
    private Integer countedQty;

    @Column(name = "system_qty", nullable = false)
    private int systemQty;

    @Column(name = "snapshot_version")
    private Long snapshotVersion;

    @Column(columnDefinition = "TEXT")
    private String note;

    public Long getId() { return id; }
    public Stocktake getStocktake() { return stocktake; }
    public void setStocktake(Stocktake stocktake) { this.stocktake = stocktake; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public Integer getCountedQty() { return countedQty; }
    public void setCountedQty(Integer countedQty) { this.countedQty = countedQty; }
    public int getSystemQty() { return systemQty; }
    public void setSystemQty(int systemQty) { this.systemQty = systemQty; }
    public Long getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(Long snapshotVersion) { this.snapshotVersion = snapshotVersion; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
