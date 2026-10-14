package com.dienmay.inventory.dto;

import jakarta.validation.constraints.*;

public class SupplierForm {

    @NotBlank(message = "Tên nhà cung cấp không được trống")
    @Size(max = 150, message = "Tên tối đa 150 ký tự")
    private String name;

    @Size(max = 20, message = "Điện thoại tối đa 20 ký tự")
    private String phone;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
