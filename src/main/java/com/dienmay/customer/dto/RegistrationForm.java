package com.dienmay.customer.dto;
import jakarta.validation.constraints.*;
public class RegistrationForm extends ProfileForm {
    @NotBlank(message="Vui lòng nhập email.") @Email(message="Email không hợp lệ.") @Size(max=254) private String email;
    @NotBlank @Size(min=8,max=72,message="Mật khẩu cần 8–72 ký tự, không quá72 byte UTF-8.") private String password;
    @NotBlank(message="Vui lòng nhập lại mật khẩu.") private String confirmPassword;
    public String getEmail(){return email;} public void setEmail(String value){email=value;}
    public String getPassword(){return password;} public void setPassword(String value){password=value;}
    public String getConfirmPassword(){return confirmPassword;} public void setConfirmPassword(String value){confirmPassword=value;}
}
