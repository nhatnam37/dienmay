package com.dienmay.config;

import com.dienmay.auth.AppUserDetailsService;
import com.dienmay.auth.login.*;
import com.dienmay.customer.auth.CustomerUserDetailsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.logout.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration @EnableMethodSecurity
@org.springframework.scheduling.annotation.EnableScheduling
@org.springframework.boot.context.properties.EnableConfigurationProperties(LoginProtectionProperties.class)
public class SecurityConfig {
    @Bean public LoginAttemptLimiter loginAttemptLimiter(ObjectProvider<java.time.Clock> clocks,LoginProtectionProperties properties){return new LoginAttemptLimiter(clocks.getIfAvailable(java.time.Clock::systemUTC),properties);}
    @Bean public LoginProtection loginProtection(LoginAttemptLimiter limiter,LoginProtectionProperties properties,PasswordEncoder encoder){return new LoginProtection(limiter,properties,encoder);}
    private void protectLogin(HttpSecurity http,LoginProtection protection,LoginAttemptLimiter.Realm realm,String path,String target,boolean always)throws Exception{
        http.headers(h->h.referrerPolicy(r->r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        http.addFilterBefore(protection.filter(realm,path),org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        var success=new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
        success.setDefaultTargetUrl(target);success.setAlwaysUseDefaultTargetUrl(always);
        // Customer phải đọc đúng saved request riêng để sau login quay lại checkout và giữ giỏ.
        success.setRequestCache(cache(realm==LoginAttemptLimiter.Realm.CUSTOMER?"CUSTOMER_SAVED_REQUEST":"EMPLOYEE_SAVED_REQUEST"));
        http.formLogin(f->f.failureHandler(protection.failure(realm,path)).successHandler((request,response,authentication)->{
            protection.succeeded(realm,request);success.onAuthenticationSuccess(request,response,authentication);
        }));
    }
    public static final String CUSTOMER_CONTEXT="CUSTOMER_SECURITY_CONTEXT";
    public static final String EMPLOYEE_CONTEXT="SPRING_SECURITY_CONTEXT";
    public static final String CUSTOMER_CSRF="CUSTOMER_CSRF";
    public static final String EMPLOYEE_CSRF="EMPLOYEE_CSRF";
    @Bean public PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean("customerContextRepository") public SecurityContextRepository customerContext(){return context(CUSTOMER_CONTEXT);}
    @Bean("employeeContextRepository") public SecurityContextRepository employeeContext(){return context(EMPLOYEE_CONTEXT);}
    @Bean("customerCsrfRepository") public CsrfTokenRepository customerCsrf(){return csrf(CUSTOMER_CSRF);}
    @Bean("employeeCsrfRepository") public CsrfTokenRepository employeeCsrf(){return csrf(EMPLOYEE_CSRF);}
    private SecurityContextRepository context(String key){var repo=new HttpSessionSecurityContextRepository();repo.setSpringSecurityContextKey(key);return repo;}
    private CsrfTokenRepository csrf(String key){var repo=new HttpSessionCsrfTokenRepository();repo.setSessionAttributeName(key);return repo;}
    private ProviderManager manager(UserDetailsService details,PasswordEncoder encoder){var provider=new DaoAuthenticationProvider(details);provider.setPasswordEncoder(encoder);provider.setHideUserNotFoundExceptions(true);return new ProviderManager(provider);}
    private HttpSessionRequestCache cache(String key){var cache=new HttpSessionRequestCache();cache.setSessionAttrName(key);return cache;}
    private void logout(HttpSecurity http,String url,String target,SecurityContextRepository contexts,CsrfTokenRepository tokens)throws Exception{
        // Không dùng invalidateHttpSession: hai context và giỏ cùng session. Chỉ xóa đúng context/CSRF của chain.
        // Filter đặt sau CsrfFilter; chỉ POST đúng URL, không có GET logout hoặc tắt CSRF.
        http.logout(AbstractHttpConfigurer::disable);
        LogoutSuccessHandler success=(request,response,authentication)->new DefaultRedirectStrategy().sendRedirect(request,response,target);
        LogoutHandler clear=(request,response,authentication)->{contexts.saveContext(SecurityContextHolder.createEmptyContext(),request,response);tokens.saveToken(null,request,response);SecurityContextHolder.clearContext();};
        LogoutFilter filter=new LogoutFilter(success,clear);filter.setLogoutRequestMatcher(new AntPathRequestMatcher(url,"POST"));http.addFilterAt(filter,LogoutFilter.class);
    }
    @Bean @Order(1)
    public SecurityFilterChain customerChain(HttpSecurity http,ObjectProvider<CustomerUserDetailsService> customers,PasswordEncoder encoder,LoginProtection loginProtection,
            @Qualifier("customerContextRepository") SecurityContextRepository contexts,@Qualifier("customerCsrfRepository") CsrfTokenRepository tokens)throws Exception{
        // Chain1 chỉ nhận public storefront + URL khách, gồm cả URL xử lý login/register/logout.
        // Chain2 là fallback; request chỉ đi qua CHAIN KHỚP ĐẦU TIÊN, không chạy chồng hai bộ đăng nhập.
        http.securityMatcher("/warranty","/warranty/**","/","/products","/products/**","/search","/register","/customer/**","/account","/account/**","/cart","/cart/**","/checkout","/checkout/**","/orders","/orders/**","/css/**","/js/**","/images/**","/uploads/**","/error","/error/**")
            .securityContext(c->c.securityContextRepository(contexts).requireExplicitSave(true))
            .authenticationManager(manager(username->customers.getObject().loadUserByUsername(username),encoder))
            .csrf(c->c.csrfTokenRepository(tokens))
            .requestCache(c->c.requestCache(cache("CUSTOMER_SAVED_REQUEST")))
            // Spring đổi session ID khi xác thực; giữ mọi thuộc tính (giỏ, context/CSRF phía còn lại).
            .sessionManagement(c->c.sessionFixation(f->f.changeSessionId()))
            .authorizeHttpRequests(a->a.requestMatchers("/account","/account/**","/checkout","/checkout/**","/orders","/orders/**").hasRole("CUSTOMER").anyRequest().permitAll())
            .formLogin(f->f.loginPage("/customer/login").loginProcessingUrl("/customer/login").failureUrl("/customer/login?error").defaultSuccessUrl("/checkout",false).permitAll());
        protectLogin(http,loginProtection,LoginAttemptLimiter.Realm.CUSTOMER,"/customer/login","/checkout",false);
        logout(http,"/customer/logout","/customer/login?logout",contexts,tokens);return http.build();
    }
    @Bean @Order(2)
    public SecurityFilterChain employeeChain(HttpSecurity http,ObjectProvider<AppUserDetailsService> employees,PasswordEncoder encoder,LoginProtection loginProtection,
            ObjectProvider<com.dienmay.organization.repository.EmployeeRepository> employeeRecords,
            @Qualifier("employeeContextRepository") SecurityContextRepository contexts,@Qualifier("employeeCsrfRepository") CsrfTokenRepository tokens)throws Exception{
        // Fallback còn lại luôn dùng context/manager nhân viên riêng; không đọc key CUSTOMER_SECURITY_CONTEXT.
        // ObjectProvider chỉ resolve UDS khi login, không cho auto-config ghép hai UDS vào một manager chung.
        http.securityContext(c->c.securityContextRepository(contexts).requireExplicitSave(true))
            .authenticationManager(manager(username->employees.getObject().loadUserByUsername(username),encoder))
            .csrf(c->c.csrfTokenRepository(tokens)).requestCache(c->c.requestCache(cache("EMPLOYEE_SAVED_REQUEST")))
            .sessionManagement(c->c.sessionFixation(f->f.changeSessionId()))
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/actuator/health").permitAll()
                // Health là endpoint duy nhất công khai; không cho cả ADMIN mở endpoint Actuator khác.
                .requestMatchers("/actuator","/actuator/**").denyAll()
                .requestMatchers("/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/inventory/serials").hasAnyRole("ADMIN","BRANCH_MANAGER","WAREHOUSE","WARRANTY_STAFF")
                .requestMatchers("/inventory/stocktakes/*/close").hasAnyRole("ADMIN","BRANCH_MANAGER")
                .requestMatchers("/inventory/suppliers","/inventory/suppliers/**").hasAnyRole("ADMIN","WAREHOUSE")
                .requestMatchers("/inventory/**").hasAnyRole("ADMIN","BRANCH_MANAGER","WAREHOUSE")
                .requestMatchers("/pos/returns","/pos/returns/**","/pos/invoices/*/returns","/pos/invoices/*/returns/**","/pos/invoices/*/cancel").hasAnyRole("ADMIN","BRANCH_MANAGER")
                .requestMatchers("/pos","/pos/**").hasAnyRole("CASHIER","BRANCH_MANAGER","ADMIN")
                .requestMatchers("/reports/api/by-branch").hasRole("ADMIN")
                .requestMatchers("/reports/inventory","/reports/low-stock","/reports/export/inventory").hasAnyRole("ADMIN","BRANCH_MANAGER","WAREHOUSE")
                .requestMatchers("/reports","/reports/**").hasAnyRole("ADMIN","BRANCH_MANAGER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/staff/warranty","/staff/warranty/**").hasAnyRole("WARRANTY_STAFF","BRANCH_MANAGER","ADMIN")
                .requestMatchers("/staff/orders","/staff/orders/**").hasAnyRole("CASHIER","BRANCH_MANAGER","ADMIN")
                .anyRequest().hasAnyRole("ADMIN","BRANCH_MANAGER","CASHIER","WAREHOUSE","WARRANTY_STAFF"))
            .formLogin(f->f.loginPage("/login").loginProcessingUrl("/login").defaultSuccessUrl("/staff",true).permitAll());
        // Tạo filter riêng trong chain, không đăng ký servlet filter toàn cục để tránh chạy sang chain khách.
        http.addFilterBefore(new com.dienmay.auth.ActiveEmployeeFilter(employeeRecords,contexts,tokens),org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        protectLogin(http,loginProtection,LoginAttemptLimiter.Realm.EMPLOYEE,"/login","/staff",true);
        logout(http,"/logout","/login?logout",contexts,tokens);return http.build();
    }
}
