package com.dienmay.customer.auth;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.stereotype.Component;
@Component public class CustomerSessionLogin {
    private final CustomerUserDetailsService details;private final SecurityContextRepository repository;private final CsrfTokenRepository csrf;
    public CustomerSessionLogin(CustomerUserDetailsService details,@Qualifier("customerContextRepository") SecurityContextRepository repository,@Qualifier("customerCsrfRepository") CsrfTokenRepository csrf){this.details=details;this.repository=repository;this.csrf=csrf;}
    public void login(String email,HttpServletRequest request,HttpServletResponse response){
        // Chỉ gọi sau register/đổi hồ sơ/mật khẩu đã thành công, không nhận một email tùy ý làm lối đăng nhập.
        CustomerUserDetails principal=(CustomerUserDetails)details.loadUserByUsername(email);if(!principal.isEnabled())throw new org.springframework.security.access.AccessDeniedException("Tài khoản không hoạt động.");principal.eraseCredentials();
        var authentication=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        request.getSession();new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication,request,response);
        // Đổi session ID giữ giỏ và context nhân viên; chỉ xoay CSRF khách, không xóa token của nhân viên.
        csrf.saveToken(null,request,response);var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context);repository.saveContext(context,request,response);
    }
}
