package com.dienmay.organization.dto;

import jakarta.validation.constraints.*;

public class BranchForm {
    @NotBlank(message = "Vui lòng nhập mã chi nhánh.")
    @Size(max = 20, message = "Mã tối đa 20 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9]+(-[A-Za-z0-9]+)*", message = "Mã chỉ gồm chữ, số và gạch ngang giữa các nhóm.")
    private String code;
    @NotBlank(message = "Vui lòng nhập tên chi nhánh.")
    @Size(max = 150, message = "Tên tối đa 150 ký tự.")
    private String name;
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
    private String address;
    @Size(max = 20, message = "Điện thoại tối đa 20 ký tự.")
    private String phone;
    private boolean active = true;
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
