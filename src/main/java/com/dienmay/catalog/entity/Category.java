package com.dienmay.catalog.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    // LAZY: chỉ tải danh mục cha khi cần; service ánh xạ DTO trong transaction.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        // Gán ngay ở Java để DTO sau save có thời gian mà không cần đọc lại DB.
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getParent() { return parent; }
    public void setParent(Category parent) { this.parent = parent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
