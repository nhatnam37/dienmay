package com.dienmay.security;

import com.dienmay.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc @Transactional
@Import(LoginProtectionIT.TestTime.class)
class LoginProtectionIT extends MySqlIntegrationTestBase {
    @TestConfiguration static class TestTime {
        @Bean @Primary LoginAttemptLimiterTest.MutableClock loginClock(){return new LoginAttemptLimiterTest.MutableClock();}
    }
    @Autowired LoginAttemptLimiterTest.MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    String staff,email,ip;
    static final String PASSWORD="SecurityTest123!";
    @BeforeEach void fixture(){
        String token=UUID.randomUUID().toString().replace("-", "").substring(0,12);staff="L9"+token;email=token+"@example.com";
        // Địa chỉ khác nhau cho từng test; bộ đếm nằm ngoài transaction DB và không được rollback.
        ip="2001:db8:"+token.substring(0,4)+":"+token.substring(4,8)+":"+token.substring(8)+"::1";
        jdbc.update("INSERT INTO employee(branch_id,role_id,username,password_hash,full_name) VALUES ((SELECT MIN(id) FROM branch),(SELECT id FROM role WHERE code='ADMIN'),?,?,?)",staff,encoder.encode(PASSWORD),"Nhân viên kiểm thử");
        String phone="09"+String.format("%08d",Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),100000000));
        jdbc.update("INSERT INTO customer(email,phone,password_hash,full_name) VALUES (?,?,?,?)",email,phone,encoder.encode(PASSWORD),"Khách kiểm thử");
    }
    private String name(String path){return path.equals("/login")?staff:email;}
    private MvcResult attempt(String path,String name,String password,String address)throws Exception{
        var session=new MockHttpSession();var page=mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
        String token=((CsrfToken)page.getRequest().getAttribute("_csrf")).getToken();
        return mvc.perform(post(path).session(session).with(r->{r.setRemoteAddr(address);return r;}).param("_csrf",token).param("username",name).param("password",password)).andReturn();
    }
    private void wrong(String path,String name,String address)throws Exception{assertThat(attempt(path,name,"wrong-password",address).getResponse().getRedirectedUrl()).isEqualTo(path+"?error");}
    private void blocked(String path,String name,String address)throws Exception{var response=attempt(path,name,PASSWORD,address).getResponse();assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(com.dienmay.auth.login.LoginProtection.BLOCKED_MESSAGE);}
    private void fiveFailuresThenBlocked(String path)throws Exception{
        for(int i=0;i<5;i++)wrong(path,name(path),ip);
        mvc.perform(get(path).param("error",""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(path.equals("/login")
                        ? "Sai tài khoản hoặc mật khẩu." : "Email hoặc mật khẩu không đúng")));
        blocked(path,name(path),ip);
    }
    @Test @TestCase("TC-SEC-05") @TestCase("TC-AUTH-07")
    @DisplayName("Nhân viên ADMIN sai mật khẩu: quay lại trang báo lỗi; sai năm lần chặn cả mật khẩu đúng")
    void login_staffWrongPassword_rendersErrorAndBlocksAfterFiveFailures()throws Exception{
        fiveFailuresThenBlocked("/login");
    }
    @Test @TestCase("TC-SEC-05")
    @DisplayName("Khách sai năm lần: mật khẩu đúng ở lần sáu vẫn bị chặn")
    void login_customerFiveFailures_rejectsCorrectPasswordUntilUnblocked()throws Exception{
        fiveFailuresThenBlocked("/customer/login");
    }
    @ParameterizedTest @ValueSource(strings={"/login","/customer/login"}) @TestCase("TC-SEC-06")
    @DisplayName("Clock giả tiến năm phút: đăng nhập đúng được chấp nhận")
    void login_blockExpired_acceptsCorrectPassword(String path)throws Exception{
        for(int i=0;i<5;i++)wrong(path,name(path),ip);blocked(path,name(path),ip);clock.advance(Duration.ofMinutes(5));
        assertThat(attempt(path,name(path),PASSWORD,ip).getResponse().getRedirectedUrl()).isEqualTo(path.equals("/login")?"/staff":"/checkout");
    }
    @ParameterizedTest @ValueSource(strings={"/login","/customer/login"}) @TestCase("TC-SEC-07")
    @DisplayName("Sai năm mươi tài khoản khác nhau: lần 51 từ IP đó bị chặn")
    void login_fiftyDifferentAccounts_blocksIpOnNextAttempt(String path)throws Exception{
        for(int i=0;i<50;i++)wrong(path,"missing"+i+"-"+staff+"@example.com",ip);blocked(path,name(path),ip);
    }
    @Test @TestCase("TC-SEC-08") @DisplayName("Chặn khách không chặn nhân viên cùng IP và ngược lại")
    void login_oneChainBlocked_otherChainStillAuthenticates()throws Exception{
        for(int i=0;i<5;i++)wrong("/customer/login",email,ip);blocked("/customer/login",email,ip);
        assertThat(attempt("/login",staff,PASSWORD,ip).getResponse().getRedirectedUrl()).isEqualTo("/staff");
        String otherIp=ip+"2";for(int i=0;i<5;i++)wrong("/login",staff,otherIp);blocked("/login",staff,otherIp);
        assertThat(attempt("/customer/login",email,PASSWORD,otherIp).getResponse().getRedirectedUrl()).isEqualTo("/checkout");
    }
    @ParameterizedTest @ValueSource(strings={"/login","/customer/login"}) @TestCase("TC-SEC-09")
    @DisplayName("Tài khoản không có vẫn bị đếm và phản hồi chặn giống tài khoản có thật")
    void login_nonexistentAccount_countsAndUsesSameBlockedResponse(String path)throws Exception{
        String missing="missing-"+email;for(int i=0;i<5;i++)wrong(path,missing,ip);blocked(path,missing,ip);
        // Chặn theo cặp, không khóa tài khoản thật trong DB.
        assertThat(attempt(path,name(path),PASSWORD,ip).getResponse().getStatus()).isEqualTo(302);
        assertThat(jdbc.queryForObject("SELECT active FROM employee WHERE username=?",Boolean.class,staff)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"/login","/customer/login"}) @TestCase("TC-SEC-10")
    @DisplayName("Đăng nhập đúng xóa bộ đếm cặp của đúng chain")
    void login_success_resetsFailedPairCounter(String path)throws Exception{
        for(int i=0;i<4;i++)wrong(path,name(path),ip);
        assertThat(attempt(path,name(path),PASSWORD,ip).getResponse().getStatus()).isEqualTo(302);
        for(int i=0;i<4;i++)wrong(path,name(path),ip);
        assertThat(attempt(path,name(path),PASSWORD,ip).getResponse().getRedirectedUrl()).isEqualTo(path.equals("/login")?"/staff":"/checkout");
    }
}
