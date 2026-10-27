package com.dienmay.customer;

import com.dienmay.config.SecurityConfig;
import com.dienmay.customer.web.CustomerController;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.customer.auth.CustomerSessionLogin;
import com.dienmay.customer.mapper.CustomerMapper;
import com.dienmay.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class,CustomerMapper.class})
class CustomerWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerService customers;
    @MockitoBean CustomerSessionLogin login;
    @Test void register_anonymous_formAccessible()throws Exception{mvc.perform(get("/register")).andExpect(status().isOk());}
    @Test void login_wrongCredentials_messageDoesNotRevealEmailExistence()throws Exception{mvc.perform(get("/customer/login?error")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Email hoặc mật khẩu không đúng")));}
    @Test void register_missingCsrf_forbidden()throws Exception{mvc.perform(post("/register")).andExpect(status().isForbidden());verifyNoInteractions(customers,login);}
    @Test @com.dienmay.support.TestCase("TC-WEB-03")
    void register_invalidFields_showsErrorsWithoutSaving()throws Exception{mvc.perform(post("/register").with(csrf()).param("email","bad").param("password","123")).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registrationForm","email","password","fullName","phone"));verifyNoInteractions(customers,login);}
    @Test @com.dienmay.support.TestCase("TC-WEB-02")
    void register_duplicateEmail_showsFormWithoutEchoingPassword()throws Exception{when(customers.register(any())).thenThrow(new BusinessException("Email đã được sử dụng."));mvc.perform(post("/register").with(csrf()).param("email","nam@example.com").param("fullName","Nam").param("phone","0912345678").param("password","Secret123").param("confirmPassword","Secret123")).andExpect(status().isOk()).andExpect(model().attributeHasErrors("registrationForm")).andExpect(content().string(org.hamcrest.Matchers.containsString("Email đã được sử dụng."))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secret123"))));verifyNoInteractions(login);}
    @Test void account_anonymous_customerLogin()throws Exception{mvc.perform(get("/account")).andExpect(redirectedUrlPattern("**/customer/login"));}
}
