package com.dienmay.security;

import com.dienmay.config.SecurityConfig;
import com.dienmay.support.MySqlIntegrationTestBase;
import com.dienmay.support.TestCase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Dùng toàn bộ ứng dụng và filter chain thật, không mock controller hay bỏ filter. */
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecuritySurfaceIT extends MySqlIntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers;
    static final List<String> IDENTITIES=List.of("ANONYMOUS","CUSTOMER","CASHIER","WAREHOUSE","WARRANTY_STAFF","BRANCH_MANAGER","ADMIN");
    record WriteEndpoint(String method,String path) {
        @Override public String toString(){return method+" "+path;}
    }
    Stream<org.junit.jupiter.params.provider.Arguments> writes(){
        Set<WriteEndpoint> endpoints=new TreeSet<>(Comparator.comparing(WriteEndpoint::toString));
        handlers.getHandlerMethods().forEach((mapping,handler)->{
            if(!handler.getBeanType().getPackageName().startsWith("com.dienmay."))return;
            // Không tự loại endpoint lạ: mọi mapping ghi mới tự tham gia kiểm tra CSRF.
            var declared=mapping.getMethodsCondition().getMethods();
            for(var method:List.of(RequestMethod.POST,RequestMethod.PUT,RequestMethod.DELETE,RequestMethod.PATCH)){
                if(declared.isEmpty()||declared.contains(method))for(String path:mapping.getPatternValues())
                    endpoints.add(new WriteEndpoint(method.name(),path.replaceAll("\\{[^}]+}","900000000")));
            }
        });
        // Form login/logout là filter endpoint, không xuất hiện trong HandlerMapping.
        for(String path:List.of("/login","/logout","/customer/login","/customer/logout"))endpoints.add(new WriteEndpoint("POST",path));
        assertThat(endpoints).as("Phải tìm thấy endpoint ghi của cả hai chain").anyMatch(e->e.path.equals("/checkout")).anyMatch(e->e.path.startsWith("/admin/"));
        return endpoints.stream().flatMap(endpoint->IDENTITIES.stream().map(identity->org.junit.jupiter.params.provider.Arguments.of(endpoint,identity)));
    }
    @ParameterizedTest(name="{0} — {1}: thiếu CSRF phải 403") @MethodSource("writes")
    @TestCase("TC-SEC-03") @DisplayName("Mọi endpoint ghi tự lấy từ HandlerMapping chặn thiếu CSRF với cả bảy danh tính")
    void csrf_anyRegisteredWriteWithoutToken_rejectsEveryIdentity(WriteEndpoint endpoint,String identity)throws Exception{
        var session=new MockHttpSession();
        if(!identity.equals("ANONYMOUS")){
            var principal=User.withUsername("security-fixture").password("not-a-real-password").roles(identity).build();
            var context=new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
            session.setAttribute(identity.equals("CUSTOMER")?SecurityConfig.CUSTOMER_CONTEXT:SecurityConfig.EMPLOYEE_CONTEXT,context);
        }
        var response=mvc.perform(request(org.springframework.http.HttpMethod.valueOf(endpoint.method),endpoint.path).session(session)).andReturn().getResponse();
        assertThat(response.getStatus()).as("%s — %s: thiếu CSRF phải bị chặn trước controller",endpoint,identity).isEqualTo(403);
    }
    @ParameterizedTest(name="Header bảo mật {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings={"/login","/customer/login"}) @TestCase("TC-SEC-04") @DisplayName("Trang đăng nhập cả hai chain có nosniff, chống nhúng và Referrer-Policy")
    void headers_bothLoginPages_protectContentFrameAndReferrer(String path)throws Exception{
            var response=mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andReturn().getResponse();
            assertThat(response.getHeader("X-Frame-Options")!=null || Optional.ofNullable(response.getHeader("Content-Security-Policy")).orElse("").contains("frame-ancestors")).as("Chống nhúng tại %s",path).isTrue();
            assertThat(response.getHeader("Referrer-Policy")).as("Referrer-Policy tại %s",path).isEqualTo("no-referrer");
    }
}
