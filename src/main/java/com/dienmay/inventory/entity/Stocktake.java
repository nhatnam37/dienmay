package com.dienmay.inventory.entity;

import jakarta.persistence.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.catalog.entity.Product;
import java.time.LocalDateTime;

@jakarta.persistence.EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Entity
@Table(name = "stocktake")
public class Stocktake {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StocktakeStatus status = StocktakeStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Employee createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @org.springframework.data.annotation.CreatedDate
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "closed_by", nullable = true)
    private Employee closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public StocktakeStatus getStatus() { return status; }
    public void setStatus(StocktakeStatus status) { this.status = status; }
    public Employee getCreatedBy() { return createdBy; }
    public void setCreatedBy(Employee createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Employee getClosedBy() { return closedBy; }
    public void setClosedBy(Employee closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
