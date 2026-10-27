package com.dienmay.customer.auth;
import com.dienmay.customer.entity.Customer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
public class CustomerUserDetails extends User {
    private final Long customerId;private final String fullName;
    public CustomerUserDetails(Customer c){super(c.getEmail(),c.getPasswordHash(),c.isActive(),true,true,true,List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));customerId=c.getId();fullName=c.getFullName();}
    public Long getCustomerId(){return customerId;}public String getFullName(){return fullName;}
}
