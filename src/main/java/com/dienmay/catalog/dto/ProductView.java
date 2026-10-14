package com.dienmay.catalog.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.dienmay.catalog.entity.ProductStatus;

// DTO bất biến: tầng web không giữ entity hoặc truy cập lazy proxy.
public class ProductView {
    private final Long id;
    private final Long categoryId;
    private final String categoryName;
    private final Long brandId;
    private final String brandName;
    private final String sku;
    private final String name;
    private final String description;
    private final BigDecimal price;
    private final boolean tracksSerial;
    private final int warrantyMonths;
    private final String imageUrl;
    private final ProductStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public ProductView(Long id, Long categoryId, String categoryName, Long brandId, String brandName, String sku, String name, String description, BigDecimal price, boolean tracksSerial, int warrantyMonths, String imageUrl, ProductStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.brandId = brandId;
        this.brandName = brandName;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.tracksSerial = tracksSerial;
        this.warrantyMonths = warrantyMonths;
        this.imageUrl = imageUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }

    public Long getCategoryId() { return categoryId; }

    public String getCategoryName() { return categoryName; }

    public Long getBrandId() { return brandId; }

    public String getBrandName() { return brandName; }

    public String getSku() { return sku; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public BigDecimal getPrice() { return price; }

    public boolean isTracksSerial() { return tracksSerial; }

    public int getWarrantyMonths() { return warrantyMonths; }

    public String getImageUrl() { return imageUrl; }

    public ProductStatus getStatus() { return status; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
