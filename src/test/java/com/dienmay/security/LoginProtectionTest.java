package com.dienmay.security;

import com.dienmay.auth.login.*;
import com.dienmay.support.TestCase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.FilterChain;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginProtectionTest {
    @ParameterizedTest @ValueSource(booleans={false,true}) @TestCase("TC-SEC-11")
    @DisplayName("XFF không được tin mặc định hoặc khi remote chưa nằm trong allowlist")
    void sourceIp_untrustedForwardedHeader_usesRemoteAddress(boolean enabled)throws Exception{
        var p=LoginAttemptLimiterTest.settings();p.setTrustForwardedFor(enabled);p.setTrustedProxyAddresses(List.of("10.0.0.2"));
        var limiter=new LoginAttemptLimiter(new LoginAttemptLimiterTest.MutableClock(),p);
        for(int i=0;i<5;i++)limiter.failed(LoginAttemptLimiter.Realm.EMPLOYEE,"10.0.0.1","admin");
        var protection=new LoginProtection(limiter,p,new BCryptPasswordEncoder());var request=new MockHttpServletRequest("POST","/login");request.setServletPath("/login");request.setRemoteAddr("10.0.0.1");request.addHeader("X-Forwarded-For","203.0.113.8");request.addParameter("username","admin");var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        protection.filter(LoginAttemptLimiter.Realm.EMPLOYEE,"/login").doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(429);verifyNoInteractions(chain);
    }
    @Test @TestCase("TC-SEC-11") @DisplayName("Chỉ proxy trong allowlist khi bật cấu hình mới được lấy IP đầu XFF")
    void sourceIp_explicitTrustedProxy_usesValidatedForwardedAddress()throws Exception{
        var p=LoginAttemptLimiterTest.settings();p.setTrustForwardedFor(true);p.setTrustedProxyAddresses(List.of("10.0.0.1"));var limiter=new LoginAttemptLimiter(new LoginAttemptLimiterTest.MutableClock(),p);
        for(int i=0;i<5;i++)limiter.failed(LoginAttemptLimiter.Realm.CUSTOMER,"203.0.113.8","mail");
        var request=new MockHttpServletRequest("POST","/customer/login");request.setServletPath("/customer/login");request.setRemoteAddr("10.0.0.1");request.addHeader("X-Forwarded-For","203.0.113.8, 10.0.0.2");request.addParameter("username","mail");var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new LoginProtection(limiter,p,new BCryptPasswordEncoder()).filter(LoginAttemptLimiter.Realm.CUSTOMER,"/customer/login").doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(429);verifyNoInteractions(chain);
    }
    @Test @TestCase("TC-SEC-09") @DisplayName("Không tồn tại và sai mật khẩu cùng chạy BCrypt, không đo latency bằng sleep")
    void authentication_missingAndWrongPassword_performsSameBcryptWork(){
        PasswordEncoder encoder=spy(new BCryptPasswordEncoder());String hash=encoder.encode("known-password");
        UserDetailsService users=name->{if(name.equals("known"))return User.withUsername(name).password(hash).roles("CUSTOMER").build();throw new UsernameNotFoundException("not found");};
        var provider=new DaoAuthenticationProvider(users);provider.setPasswordEncoder(encoder);provider.setHideUserNotFoundExceptions(true);var manager=new ProviderManager(provider);
        // Khởi tạo dummy hash của DaoAuthenticationProvider trước khi đo số phép BCrypt.
        assertThatThrownBy(()->manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("known","wrong"))).isInstanceOf(BadCredentialsException.class);
        clearInvocations(encoder);
        assertThatThrownBy(()->manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("known","wrong"))).isInstanceOf(BadCredentialsException.class);verify(encoder,times(1)).matches(eq("wrong"),anyString());
        clearInvocations(encoder);
        assertThatThrownBy(()->manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("missing","wrong"))).isInstanceOf(BadCredentialsException.class);verify(encoder,times(1)).matches(eq("wrong"),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"face","localhost","203.0.113.999","","127.1","..."}) @TestCase("TC-SEC-11")
    @DisplayName("XFF không phải literal IP hợp lệ quay về remoteAddr, không tra DNS")
    void sourceIp_invalidForwardedAddress_fallsBackToRemote(String forwarded)throws Exception{
        var p=LoginAttemptLimiterTest.settings();p.setTrustForwardedFor(true);p.setTrustedProxyAddresses(List.of("10.0.0.1"));var limiter=new LoginAttemptLimiter(new LoginAttemptLimiterTest.MutableClock(),p);
        for(int i=0;i<5;i++)limiter.failed(LoginAttemptLimiter.Realm.EMPLOYEE,"10.0.0.1","admin");
        var request=new MockHttpServletRequest("POST","/login");request.setServletPath("/login");request.setRemoteAddr("10.0.0.1");request.addHeader("X-Forwarded-For",forwarded);request.addParameter("username","admin");var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new LoginProtection(limiter,p,new BCryptPasswordEncoder()).filter(LoginAttemptLimiter.Realm.EMPLOYEE,"/login").doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(429);verifyNoInteractions(chain);
    }
}
