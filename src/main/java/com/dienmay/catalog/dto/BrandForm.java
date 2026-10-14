package com.dienmay.catalog.dto;

import jakarta.validation.constraints.*;

public class BrandForm {

    @NotBlank(message = "Tên thương hiệu không được để trống")
    @Size(max = 100, message = "Tên thương hiệu tối đa 100 ký tự")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
