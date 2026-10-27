package com.dienmay.customer.auth;
import com.dienmay.customer.repository.CustomerRepository;
import com.dienmay.customer.service.CustomerService;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class CustomerUserDetailsService implements UserDetailsService {
    private final CustomerRepository customers;
    public CustomerUserDetailsService(CustomerRepository customers){this.customers=customers;}
    @Override @Transactional(readOnly=true) public UserDetails loadUserByUsername(String email){return new CustomerUserDetails(customers.findByEmail(CustomerService.email(email)).orElseThrow(()->new UsernameNotFoundException("Email hoặc mật khẩu không đúng.")));}
}
