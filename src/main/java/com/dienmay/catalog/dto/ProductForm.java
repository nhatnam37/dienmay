package com.dienmay.catalog.dto;

import jakarta.validation.constraints.*;
import com.dienmay.catalog.entity.SerialType;
import java.math.BigDecimal;

public class ProductForm {

    @NotNull(message = "Vui lòng chọn danh mục")
    @Positive(message = "Mã danh mục phải lớn hơn 0")
    private Long categoryId;

    @NotNull(message = "Vui lòng chọn thương hiệu")
    @Positive(message = "Mã thương hiệu phải lớn hơn 0")
    private Long brandId;

    @NotBlank(message = "SKU không được để trống")
    @Size(max = 50, message = "SKU tối đa 50 ký tự")
    @Pattern(regexp = "[A-Za-z0-9]+(-[A-Za-z0-9]+)*", message = "SKU chỉ gồm chữ, số và gạch ngang giữa các nhóm")
    private String sku;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.00", message = "Giá phải lớn hơn hoặc bằng 0")
    @Digits(integer = 13, fraction = 2, message = "Giá tối đa 13 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal price;

    @NotNull(message = "Vui lòng chọn chế độ quản lý serial")
    private Boolean tracksSerial = false;

    private SerialType serialType;

    @NotNull(message = "Thời hạn bảo hành không được để trống")
    @Min(value = 0, message = "Bảo hành tối thiểu 0 tháng")
    @Max(value = 60, message = "Bảo hành tối đa 60 tháng")
    private Integer warrantyMonths = 0;

    @Size(max = 255, message = "Đường dẫn ảnh tối đa 255 ký tự")
    private String imageUrl;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Boolean getTracksSerial() { return tracksSerial; }
    public void setTracksSerial(Boolean tracksSerial) { this.tracksSerial = tracksSerial; }

    public SerialType getSerialType() { return serialType; }
    public void setSerialType(SerialType serialType) { this.serialType = serialType; }

    @AssertTrue(message = "Bật theo dõi Serial phải chọn IMEI hoặc GENERIC; không theo dõi thì loại Serial phải để trống.")
    public boolean isSerialConfigurationValid() {
        return tracksSerial == null || (tracksSerial ? serialType != null : serialType == null);
    }

    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
