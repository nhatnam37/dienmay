package com.dienmay.customer.dto;
import jakarta.validation.constraints.*;
public class PasswordForm {
    @NotBlank(message="Vui lòng nhập mật khẩu cũ.") private String oldPassword;
    @NotBlank @Size(min=8,max=72,message="Mật khẩu mới cần 8–72 ký tự, không quá72 byte UTF-8.") private String password;
    @NotBlank(message="Vui lòng nhập lại mật khẩu mới.") private String confirmPassword;
    public String getOldPassword(){return oldPassword;} public void setOldPassword(String value){oldPassword=value;}
    public String getPassword(){return password;} public void setPassword(String value){password=value;}
    public String getConfirmPassword(){return confirmPassword;} public void setConfirmPassword(String value){confirmPassword=value;}
}
