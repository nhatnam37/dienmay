package com.dienmay.auth.login;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.InetAddress;

/** Cùng cách xác định IP/tên ở filter, success và failure handler để không đếm sai key. */
public final class LoginProtection {
    public static final String BLOCKED_MESSAGE="Quá nhiều lần thử, vui lòng thử lại sau";
    private final LoginAttemptLimiter limiter;
    private final LoginProtectionProperties properties;
    private final PasswordEncoder encoder;
    private final String dummyHash;
    public LoginProtection(LoginAttemptLimiter limiter,LoginProtectionProperties properties,PasswordEncoder encoder){
        this.limiter=limiter;this.properties=properties;this.encoder=encoder;
        dummyHash=encoder.encode(java.util.UUID.randomUUID().toString());
    }
    public OncePerRequestFilter filter(LoginAttemptLimiter.Realm realm,String loginPath){return new OncePerRequestFilter(){
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
            if(new org.springframework.security.web.util.matcher.AntPathRequestMatcher(loginPath,"POST").matches(request)&&limiter.isBlocked(realm,ip(request),username(request))){
                response.setStatus(429);response.setCharacterEncoding("UTF-8");response.setContentType("text/plain;charset=UTF-8");
                response.setHeader("Cache-Control","no-store");response.getWriter().write(BLOCKED_MESSAGE);return;
            }
            chain.doFilter(request,response);
        }
    };}
    public AuthenticationFailureHandler failure(LoginAttemptLimiter.Realm realm,String loginPath){return (request,response,error)->{
        // DaoAuthenticationProvider đã chạy một BCrypt khi sai mật khẩu hoặc không có username
        // (hideUserNotFoundExceptions=true). Bù cho tài khoản disabled/locked bị pre-check sớm.
        if(error instanceof AccountStatusException)encoder.matches(request.getParameter("password")==null?"":request.getParameter("password"),dummyHash);
        limiter.failed(realm,ip(request),username(request));
        response.sendRedirect(request.getContextPath()+loginPath+"?error");
    };}
    public void succeeded(LoginAttemptLimiter.Realm realm,HttpServletRequest request){limiter.succeeded(realm,ip(request),username(request));}
    String username(HttpServletRequest request){String value=request.getParameter("username");return value==null?"":value;}
    String ip(HttpServletRequest request){
        String remote=request.getRemoteAddr();
        // Chỉ bật khi proxy đã được cấu hình xóa/ghi đè XFF từ client và remote nằm trong allowlist.
        if(!properties.isTrustForwardedFor()||!properties.getTrustedProxyAddresses().contains(remote))return remote;
        String header=request.getHeader("X-Forwarded-For");if(header==null)return remote;
        String first=header.split(",",2)[0].strip();
        if(first.contains(":")){
            if(!first.matches("[0-9a-fA-F:.]+"))return remote;
        }else{
            if(!first.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))return remote;
            for(String octet:first.split("\\."))if(Integer.parseInt(octet)>255)return remote;
        } // Chỉ literal IPv4/IPv6, không DNS lookup tên do client gửi.
        try{return InetAddress.getByName(first).getHostAddress();}catch(java.net.UnknownHostException invalid){return remote;}
    }
}
