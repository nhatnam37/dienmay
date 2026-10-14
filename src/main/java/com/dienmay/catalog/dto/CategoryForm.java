package com.dienmay.catalog.dto;

import jakarta.validation.constraints.*;

public class CategoryForm {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
    private String name;

    @Positive(message = "Mã danh mục cha phải lớn hơn 0")
    private Long parentId;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
}
