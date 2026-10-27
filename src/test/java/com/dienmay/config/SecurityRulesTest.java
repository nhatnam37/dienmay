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
class SecurityRulesTest extends com.dienmay.support.ActiveEmployeeWebFixture {

    @Autowired
    private MockMvc mockMvc;

    // TC-AUTH-04: chưa đăng nhập được chuyển đến trang login.
    @com.dienmay.support.TestCase("TC-AUTH-04")
    @org.junit.jupiter.api.DisplayName("chưa đăng nhập được chuyển đến trang login.")
    @Test
    void staff_whenAnonymous_redirectsToEmployeeLogin() throws Exception {
        mockMvc.perform(get("/staff"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // TC-AUTH-05: thu ngân không được vào trang quản trị.
    @com.dienmay.support.TestCase("TC-AUTH-05")
    @org.junit.jupiter.api.DisplayName("thu ngân không được vào trang quản trị.")
    @Test
    @WithMockUser(roles = "CASHIER")
    void admin_whenCashier_isForbidden() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    // TC-AUTH-06: ADMIN được vào trang quản trị.
    @com.dienmay.support.TestCase("TC-AUTH-06")
    @org.junit.jupiter.api.DisplayName("ADMIN được vào trang quản trị.")
    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_whenAdmin_isOk() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk());
    }
}
