package com.dienmay.config;

import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.entity.Role;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.organization.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev") // Chỉ tạo tài khoản mẫu khi profile dev được bật.
public class DevDataInitializer implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(EmployeeRepository employeeRepository,
                              RoleRepository roleRepository,
                              BranchRepository branchRepository,
                              PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.roleRepository = roleRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Branch hq = branchRepository.findByCode("HQ").orElseThrow();
        createIfMissing("admin", "Quản trị viên", "ADMIN", "Admin@123", hq);
        createIfMissing("cashier01", "Thu ngân 01", "CASHIER", "Cashier@123", hq);
    }

    private void createIfMissing(String username, String fullName, String roleCode,
                                 String rawPassword, Branch branch) {
        if (employeeRepository.existsByUsername(username)) {
            return;
        }

        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        Employee e = new Employee();
        e.setUsername(username);
        e.setFullName(fullName);
        e.setPasswordHash(passwordEncoder.encode(rawPassword));
        e.setRole(role);
        e.setBranch(branch);
        employeeRepository.save(e);
    }
}
