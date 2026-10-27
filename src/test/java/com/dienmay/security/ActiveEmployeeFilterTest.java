package com.dienmay.security;

import com.dienmay.auth.ActiveEmployeeFilter;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.support.InventoryTestUsers;
import com.dienmay.support.TestCase;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveEmployeeFilterTest {
    @Mock ObjectProvider<EmployeeRepository> provider;
    @Mock EmployeeRepository employees;
    @Mock SecurityContextRepository contexts;
    @Mock CsrfTokenRepository csrf;
    @Mock FilterChain chain;
    @AfterEach void clearContext(){SecurityContextHolder.clearContext();}
    private void login(){var user=InventoryTestUsers.user("ADMIN",1,7);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities()));}
    @Test @TestCase("TC-SEC-02") @DisplayName("Nhân viên còn hoạt động tiếp tục qua filter với một lần kiểm tra DB")
    void filter_activeEmployee_continuesWithDatabaseCheck()throws Exception{
        login();when(provider.getObject()).thenReturn(employees);when(employees.existsByIdAndActiveTrue(7L)).thenReturn(true);
        var request=new MockHttpServletRequest("GET","/staff");var response=new MockHttpServletResponse();
        new ActiveEmployeeFilter(provider,contexts,csrf).doFilter(request,response,chain);
        verify(chain).doFilter(request,response);verify(employees).existsByIdAndActiveTrue(7L);verifyNoInteractions(contexts,csrf);
    }
    @Test @TestCase("TC-SEC-02") @DisplayName("Nhân viên đã xóa hoặc bị khóa bị chặn trước controller")
    void filter_missingOrInactiveEmployee_clearsOnlyEmployeeState()throws Exception{
        login();when(provider.getObject()).thenReturn(employees);when(employees.existsByIdAndActiveTrue(7L)).thenReturn(false);
        var request=new MockHttpServletRequest("POST","/admin/catalog/products");var session=new MockHttpSession();request.setSession(session);session.setAttribute("EMPLOYEE_SAVED_REQUEST","old");session.setAttribute("CUSTOMER_SAVED_REQUEST","keep");session.setAttribute("cart-fixture","keep");var response=new MockHttpServletResponse();
        new ActiveEmployeeFilter(provider,contexts,csrf).doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(403);assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();assertThat(session.isInvalid()).isFalse();assertThat(session.getAttribute("EMPLOYEE_SAVED_REQUEST")).isNull();assertThat(session.getAttribute("CUSTOMER_SAVED_REQUEST")).isEqualTo("keep");assertThat(session.getAttribute("cart-fixture")).isEqualTo("keep");verifyNoInteractions(chain);verify(csrf).saveToken(null,request,response);verify(contexts).saveContext(any(),eq(request),eq(response));
    }
    @Test @TestCase("TC-SEC-02") @DisplayName("Request ẩn danh không truy vấn DB hoặc tạo session")
    void filter_anonymousRequest_continuesWithoutDatabaseOrSession()throws Exception{
        var request=new MockHttpServletRequest("GET","/login");var response=new MockHttpServletResponse();new ActiveEmployeeFilter(provider,contexts,csrf).doFilter(request,response,chain);verify(chain).doFilter(request,response);verifyNoInteractions(provider,employees,contexts,csrf);assertThat(request.getSession(false)).isNull();
    }
}
