package com.dienmay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity // Cho phép dùng @PreAuthorize ở tầng service về sau.
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/login", "/error", "/uploads/**").permitAll()
                        // UC10 chỉ đọc xuyên chi nhánh cho bảo hành; không mở quyền ghi inventory.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/inventory/serials").hasAnyRole("ADMIN", "BRANCH_MANAGER", "WAREHOUSE", "WARRANTY_STAFF")
                        .requestMatchers("/inventory/suppliers", "/inventory/suppliers/**").hasAnyRole("ADMIN", "WAREHOUSE")
                        .requestMatchers("/inventory/**").hasAnyRole("ADMIN", "BRANCH_MANAGER", "WAREHOUSE")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }
}
