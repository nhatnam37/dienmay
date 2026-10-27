package com.dienmay.customer.mapper;
import com.dienmay.customer.entity.Customer;
import com.dienmay.customer.dto.*;
import org.springframework.stereotype.Component;
@Component public class CustomerMapper {
    public CustomerView view(Customer c){return new CustomerView(c.getId(),c.getEmail(),c.getPhone(),c.getFullName(),c.getAddress());}
    public ProfileForm form(CustomerView c){ProfileForm f=new ProfileForm();f.setFullName(c.fullName());f.setPhone(c.phone());f.setAddress(c.address());return f;}
    public void profile(ProfileForm f,Customer c){c.setFullName(f.getFullName());c.setPhone(f.getPhone());c.setAddress(f.getAddress());}
}
