package com.dienmay.security;

import com.dienmay.support.MySqlIntegrationTestBase;
import com.dienmay.support.TestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** DB riêng Testcontainers; rollback fixture, không khóa tài khoản dev của người dùng. */
@AutoConfigureMockMvc
@Transactional
class LockedEmployeeSessionIT extends MySqlIntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    @TestCase("TC-SEC-02")
    @DisplayName("Nhân viên bị khóa không được tiếp tục dùng phiên quản trị đã đăng nhập")
    void authorization_employeeDisabledAfterLogin_rejectsExistingSession() throws Exception {
        String username="S9B"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        String password="SecurityTest123!";
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name,active) VALUES ((SELECT MIN(id) FROM branch),(SELECT id FROM role WHERE code='ADMIN'),?,?,?,1)",username,encoder.encode(password),"Nhân viên kiểm thử bảo mật");
        var session=new MockHttpSession();
        mvc.perform(post("/login").session(session).param("_csrf",token("/login",session)).param("username",username).param("password",password))
                .andExpect(redirectedUrl("/staff"));
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
        jdbc.update("UPDATE employee SET active=0 WHERE username=?",username);
        // JDBC fixture không đi qua JPA: bỏ entity cache trong transaction test dùng chung,
        // giống request đăng nhập mới ngoài thực tế có persistence context mới.
        entityManager.clear();
        // Chứng minh DB thực sự đã khóa, đăng nhập mới cũng bị chặn.
        assertThat(jdbc.queryForObject("SELECT active FROM employee WHERE username=?",Boolean.class,username)).isFalse();
        var freshSession=new MockHttpSession();
        mvc.perform(post("/login").session(freshSession).param("_csrf",token("/login",freshSession)).param("username",username).param("password",password))
                .andExpect(redirectedUrl("/login?error"));
        int status=mvc.perform(get("/admin").session(session)).andReturn().getResponse().getStatus();
        // Giữ kỳ vọng an toàn; không đổi thành isOk để làm xanh lỗi bảo mật thật.
        assertThat(status).as("Nhân viên active=false phải bị chặn ở request tiếp theo bằng phiên cũ; thực tế HTTP %s",status)
                .isIn(302,401,403);
    }
    @Test @TestCase("TC-SEC-02")
    @DisplayName("Thu hồi phiên nhân viên giữ nguyên khách, giỏ và CSRF của khách; chặn POST")
    void authorization_disabledEmployee_preservesCustomerAndCartAndBlocksPost() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-", "").substring(0,12);
        String username="S9B"+suffix, password="SecurityTest123!", email=suffix+"@example.com";
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name,active) VALUES ((SELECT MIN(id) FROM branch),(SELECT id FROM role WHERE code='ADMIN'),?,?,?,1)",username,encoder.encode(password),"Nhân viên kiểm thử");
        String phone="09"+String.format("%08d",Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100000000));
        jdbc.update("INSERT INTO customer(email,phone,password_hash,full_name,active) VALUES (?,?,?,?,1)",email,phone,encoder.encode(password),"Khách kiểm thử");
        var session=new MockHttpSession();
        mvc.perform(post("/login").session(session).param("_csrf",token("/login",session)).param("username",username).param("password",password)).andExpect(redirectedUrl("/staff"));
        mvc.perform(post("/customer/login").session(session).param("_csrf",token("/customer/login",session)).param("username",email).param("password",password)).andExpect(status().is3xxRedirection());
        var customer=session.getAttribute(com.dienmay.config.SecurityConfig.CUSTOMER_CONTEXT);
        var cart=com.dienmay.storefront.web.CartController.state(session);
        mvc.perform(get("/cart").session(session)).andExpect(status().isOk());
        var customerCsrf=session.getAttribute(com.dienmay.config.SecurityConfig.CUSTOMER_CSRF);
        String staffToken=token("/staff",session);
        var before=jdbc.queryForObject("SELECT COUNT(*) FROM branch",Long.class);
        jdbc.update("UPDATE employee SET active=0 WHERE username=?",username);
        entityManager.clear();
        mvc.perform(post("/admin/organization/branches").session(session).param("_csrf",staffToken).param("code","SEC"+suffix).param("name","Không được tạo")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM branch",Long.class)).isEqualTo(before);
        assertThat(session.isInvalid()).isFalse();
        assertThat(session.getAttribute(com.dienmay.config.SecurityConfig.EMPLOYEE_CONTEXT)).isNull();
        assertThat(session.getAttribute(com.dienmay.config.SecurityConfig.EMPLOYEE_CSRF)).isNull();
        assertThat(session.getAttribute(com.dienmay.config.SecurityConfig.CUSTOMER_CONTEXT)).isSameAs(customer);
        assertThat(session.getAttribute(com.dienmay.config.SecurityConfig.CUSTOMER_CSRF)).isSameAs(customerCsrf);
        assertThat(com.dienmay.storefront.web.CartController.state(session)).isSameAs(cart);
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
        mvc.perform(get("/staff").session(session)).andExpect(redirectedUrlPattern("**/login"));
    }
    // Dùng token thực từ filter: csrf() của MockMvc thay repository trong filter dùng chung,
    // có thể làm sai kiểm thử cô lập token giữa hai chain ở lớp IT khác cùng context.
    private String token(String path,MockHttpSession session) throws Exception {
        var result=mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
        return ((org.springframework.security.web.csrf.CsrfToken)result.getRequest().getAttribute("_csrf")).getToken();
    }
}
