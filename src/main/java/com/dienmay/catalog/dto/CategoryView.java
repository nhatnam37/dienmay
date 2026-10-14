package com.dienmay.catalog.dto;

import java.time.LocalDateTime;

// DTO bất biến: tầng web không giữ entity hoặc truy cập lazy proxy.
public class CategoryView {
    private final Long id;
    private final String name;
    private final Long parentId;
    private final String parentName;
    private final LocalDateTime createdAt;

    public CategoryView(Long id, String name, Long parentId, String parentName, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.parentName = parentName;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }

    public String getName() { return name; }

    public Long getParentId() { return parentId; }

    public String getParentName() { return parentName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
