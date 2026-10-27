package com.dienmay.customer.service;
import com.dienmay.customer.dto.*;
import com.dienmay.customer.entity.Customer;
import com.dienmay.customer.mapper.CustomerMapper;
import com.dienmay.customer.repository.CustomerRepository;
import com.dienmay.common.exception.*;
import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
@Service @Transactional(readOnly=true)
public class CustomerService {
    private final CustomerRepository customers;private final PasswordEncoder encoder;private final Validator validator;private final CustomerMapper mapper;
    public CustomerService(CustomerRepository customers,PasswordEncoder encoder,Validator validator,CustomerMapper mapper){this.customers=customers;this.encoder=encoder;this.validator=validator;this.mapper=mapper;}
    public static String email(String email){return email==null?null:email.strip().toLowerCase(Locale.ROOT);}
    public static String phone(String raw){String number=raw==null?"":raw.strip();if(number.startsWith("+84"))number="0"+number.substring(3);if(!number.matches("0[35789][0-9]{8}"))throw new BusinessException("Số điện thoại di động Việt Nam không hợp lệ.");return number;}
    @Transactional public CustomerView register(RegistrationForm form){
        if(form==null)throw new BusinessException("Thiếu thông tin đăng ký.");normalize(form);form.setEmail(email(form.getEmail()));validate(form);password(form.getPassword(),form.getConfirmPassword());
        if(customers.existsByEmail(form.getEmail()))throw new BusinessException("Email đã được sử dụng.");
        if(customers.existsByPhone(form.getPhone()))throw new BusinessException("Số điện thoại đã được sử dụng.");
        Customer customer=new Customer();customer.setEmail(form.getEmail());mapper.profile(form,customer);customer.setPasswordHash(encoder.encode(form.getPassword()));
        return save(customer);
    }
    public CustomerView get(Long id){return mapper.view(find(id,false));}
    @Transactional public CustomerView update(Long id,ProfileForm form){normalize(form);validate(form);Customer customer=find(id,true);if(customers.existsByPhoneAndIdNot(form.getPhone(),id))throw new BusinessException("Số điện thoại đã được sử dụng.");mapper.profile(form,customer);return save(customer);}
    @Transactional public CustomerView changePassword(Long id,PasswordForm form){validate(form);password(form.getPassword(),form.getConfirmPassword());Customer customer=find(id,true);if(!encoder.matches(form.getOldPassword(),customer.getPasswordHash()))throw new BusinessException("Mật khẩu cũ không đúng.");customer.setPasswordHash(encoder.encode(form.getPassword()));return save(customer);}
    private void password(String value,String confirm){if(value==null||value.length()<8||value.getBytes(StandardCharsets.UTF_8).length>72)throw new BusinessException("Mật khẩu tối thiểu 8 ký tự và tối đa 72 byte UTF-8.");if(!value.equals(confirm))throw new BusinessException("Mật khẩu nhập lại không khớp.");}
    private void normalize(ProfileForm form){if(form==null)throw new BusinessException("Thiếu thông tin hồ sơ.");form.setFullName(trim(form.getFullName()));form.setAddress(trim(form.getAddress()));form.setPhone(phone(form.getPhone()));}
    private String trim(String value){return value==null||value.isBlank()?null:value.strip();}
    private void validate(Object form){if(form==null)throw new BusinessException("Thiếu thông tin.");var errors=validator.validate(form);if(!errors.isEmpty())throw new BusinessException(errors.stream().map(v->v.getMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));}
    private Customer find(Long id,boolean lock){Customer c=(lock?customers.findForUpdate(id):customers.findById(id)).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy khách hàng."));if(!c.isActive())throw new org.springframework.security.access.AccessDeniedException("Tài khoản khách không còn hoạt động.");return c;}
    private CustomerView save(Customer c){try{return mapper.view(customers.saveAndFlush(c));}catch(DataIntegrityViolationException error){throw new BusinessException("Email hoặc số điện thoại đã được sử dụng.");}}
}
