package com.dienmay.auth;

import com.dienmay.organization.repository.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Thu hồi quyền ở request tiếp theo, không chờ session cũ hết hạn. */
public final class ActiveEmployeeFilter extends OncePerRequestFilter {
    private final ObjectProvider<EmployeeRepository> employees;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository tokens;
    public ActiveEmployeeFilter(ObjectProvider<EmployeeRepository> employees, SecurityContextRepository contexts, CsrfTokenRepository tokens) {
        this.employees=employees;this.contexts=contexts;this.tokens=tokens;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.getPrincipal() instanceof AppUserDetails employee
                && !employees.getObject().existsByIdAndActiveTrue(employee.getEmployeeId())) {
            // Không cache: active=false phải có hiệu lực ngay request kế tiếp. Truy vấn theo PK,
            // không đọc lại entity từ persistence context và không invalidate session dùng chung.
            contexts.saveContext(SecurityContextHolder.createEmptyContext(),request,response);
            tokens.saveToken(null,request,response);
            var session=request.getSession(false);
            if(session!=null)session.removeAttribute("EMPLOYEE_SAVED_REQUEST");
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return; // Kể cả POST: không chạy controller/nghiệp vụ sau khi đã thu hồi quyền.
        }
        chain.doFilter(request,response);
    }
}
