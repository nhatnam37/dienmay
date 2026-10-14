package com.dienmay.catalog.dto;

import java.time.LocalDateTime;

// DTO bất biến: tầng web không giữ entity hoặc truy cập lazy proxy.
public class BrandView {
    private final Long id;
    private final String name;
    private final LocalDateTime createdAt;

    public BrandView(Long id, String name, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }

    public String getName() { return name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
