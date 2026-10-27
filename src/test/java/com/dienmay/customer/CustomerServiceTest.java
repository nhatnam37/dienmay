package com.dienmay.customer;

import com.dienmay.customer.dto.*;
import com.dienmay.customer.entity.Customer;
import com.dienmay.customer.mapper.CustomerMapper;
import com.dienmay.customer.repository.CustomerRepository;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    private static ValidatorFactory factory;
    private CustomerRepository customers;
    private PasswordEncoder encoder;
    private CustomerService service;
    @BeforeAll static void validation(){factory=Validation.buildDefaultValidatorFactory();}
    @AfterAll static void close(){factory.close();}
    @BeforeEach void setup(){customers=mock(CustomerRepository.class);encoder=mock(PasswordEncoder.class);service=new CustomerService(customers,encoder,factory.getValidator(),new CustomerMapper());}
    private RegistrationForm form(){var f=new RegistrationForm();f.setFullName("Nguyễn Văn Nam");f.setEmail("NAM@example.com");f.setPhone("+84912345678");f.setPassword("Password123");f.setConfirmPassword("Password123");return f;}
    @Test @com.dienmay.support.TestCase("TC-WEB-02")
    void register_duplicateEmail_rejected(){when(customers.existsByEmail("nam@example.com")).thenReturn(true);assertThatThrownBy(()->service.register(form())).isInstanceOf(BusinessException.class).hasMessageContaining("Email");verify(customers,never()).saveAndFlush(any());}
    @Test @com.dienmay.support.TestCase("TC-WEB-02")
    void register_duplicatePhone_rejected(){when(customers.existsByPhone("0912345678")).thenReturn(true);assertThatThrownBy(()->service.register(form())).hasMessageContaining("điện thoại");verify(customers,never()).saveAndFlush(any());}
    @Test @com.dienmay.support.TestCase("TC-WEB-03")
    void register_shortPassword_rejected(){var f=form();f.setPassword("1234567");f.setConfirmPassword("1234567");assertThatThrownBy(()->service.register(f)).isInstanceOf(BusinessException.class);}
    @Test void register_confirmationMismatch_rejected(){var f=form();f.setConfirmPassword("Another123");assertThatThrownBy(()->service.register(f)).hasMessageContaining("không khớp");}
    @Test void register_multibytePasswordOverBcryptLimit_rejected(){var f=form();f.setPassword("ế".repeat(25));f.setConfirmPassword(f.getPassword());assertThatThrownBy(()->service.register(f)).hasMessageContaining("byte");}
    @Test @com.dienmay.support.TestCase("TC-WEB-01")
    void register_validInput_normalizedAndHashed(){when(encoder.encode("Password123")).thenReturn("hash");when(customers.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));var result=service.register(form());assertThat(result.email()).isEqualTo("nam@example.com");assertThat(result.phone()).isEqualTo("0912345678");verify(encoder).encode("Password123");verify(customers).saveAndFlush(argThat(c->c.getPasswordHash().equals("hash")));}
    @Test void changePassword_wrongOldPassword_rejected(){var c=new Customer();c.setPasswordHash("hash");when(customers.findForUpdate(1L)).thenReturn(Optional.of(c));var f=new PasswordForm();f.setOldPassword("wrong");f.setPassword("NewPassword123");f.setConfirmPassword(f.getPassword());assertThatThrownBy(()->service.changePassword(1L,f)).hasMessageContaining("Mật khẩu cũ");verify(customers,never()).saveAndFlush(any());}
    @Test void update_duplicatePhone_rejected(){when(customers.findForUpdate(1L)).thenReturn(Optional.of(new Customer()));when(customers.existsByPhoneAndIdNot("0912345678",1L)).thenReturn(true);assertThatThrownBy(()->service.update(1L,form())).hasMessageContaining("điện thoại");}
    @Test void phone_invalidPrefix_rejected(){assertThatThrownBy(()->CustomerService.phone("0212345678")).isInstanceOf(BusinessException.class);}
}
