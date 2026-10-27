package com.dienmay.customer.dto;
import jakarta.validation.constraints.*;
public class ProfileForm {
    @NotBlank(message="Vui lòng nhập họ tên.") @Size(max=100,message="Họ tên tối đa100 ký tự.") private String fullName;
    @NotBlank(message="Vui lòng nhập số điện thoại.") @Size(max=20) private String phone;
    @Size(max=255,message="Địa chỉ tối đa255 ký tự.") private String address;
    public String getFullName(){return fullName;} public void setFullName(String value){fullName=value;}
    public String getPhone(){return phone;} public void setPhone(String value){phone=value;}
    public String getAddress(){return address;} public void setAddress(String value){address=value;}
}
