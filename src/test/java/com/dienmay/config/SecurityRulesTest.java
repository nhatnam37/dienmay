package com.dienmay.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Chỉ nạp tầng web và SecurityConfig, không cần MySQL.
@WebMvcTest(controllers = {com.dienmay.web.HomeController.class,
        com.dienmay.web.LoginController.class, com.dienmay.web.AdminController.class})
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    // TC-AUTH-04: chưa đăng nhập được chuyển đến trang login.
    @Test
    void home_whenAnonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // TC-AUTH-05: thu ngân không được vào trang quản trị.
    @Test
    @WithMockUser(roles = "CASHIER")
    void admin_whenCashier_isForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    // TC-AUTH-06: ADMIN được vào trang quản trị.
    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_whenAdmin_isOk() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk());
    }
}
