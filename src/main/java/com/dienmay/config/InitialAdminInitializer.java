package com.dienmay.config;

import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bootstrap triển khai, không tạo tài khoản demo và không reset mật khẩu mỗi lần restart. */
@Component
@Profile("(dev | prod) & !demo & !test & !perf")
public class InitialAdminInitializer implements CommandLineRunner {
    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final BranchRepository branches;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public InitialAdminInitializer(EmployeeRepository employees, RoleRepository roles,
            BranchRepository branches, PasswordEncoder encoder,
            @Value("${app.bootstrap.username:admin}") String username,
            @Value("${app.bootstrap.password:}") String password) {
        this.employees=employees;this.roles=roles;this.branches=branches;this.encoder=encoder;
        this.username=username;this.password=password;
    }

    @Override @Transactional
    public void run(String... args) {
        if(!username.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,49}"))
            throw new IllegalStateException("Tên admin ban đầu không hợp lệ.");
        var existing=employees.findWithRoleAndBranchByUsername(username);
        if(existing.isPresent()){
            if(!existing.get().getRole().getCode().equals("ADMIN"))
                throw new IllegalStateException("Tên admin ban đầu đã được dùng cho vai trò khác.");
            return; // Giữ mật khẩu và trạng thái hiện có, không tự mở khóa tài khoản.
        }
        if(password.length()<16 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || !password.matches(".*[A-Z].*")
                || !password.matches(".*[a-z].*") || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*") || password.startsWith("CHANGE_ME"))
            throw new IllegalStateException("DB mới yêu cầu INITIAL_ADMIN_PASSWORD mạnh: 16–72 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt.");
        var employee=new Employee();employee.setUsername(username);employee.setFullName("Quản trị viên");
        employee.setBranch(branches.findByCode("HQ").orElseThrow());
        employee.setRole(roles.findByCode("ADMIN").orElseThrow());
        employee.setPasswordHash(encoder.encode(password));employees.save(employee);
    }
}
