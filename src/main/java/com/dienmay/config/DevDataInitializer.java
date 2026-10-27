package com.dienmay.config;

import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.entity.Role;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.organization.repository.RoleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.demo.seed",havingValue="true")
@Profile("demo & !prod & !dev & !test & !perf") // Tài khoản mẫu tuyệt đối không tạo khi bật prod/dev/test.
public class DevDataInitializer {

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

    @Transactional
    public void run(String... args) {
        initialize();
    }

    private void initialize(){
        Branch hq = branchRepository.findByCode("HQ").orElseThrow();
        createIfMissing("admin", "Quản trị viên", "ADMIN", "Admin@123", hq);
        createIfMissing("cashier01", "Thu ngân 01", "CASHIER", "Cashier@123", hq);
        createIfMissing("warranty01", "Bảo hành 01", "WARRANTY_STAFF", "Warranty@123", hq);
        createIfMissing("manager01", "Quản lý HQ", "BRANCH_MANAGER", "Manager@123", hq);
        Branch cn2=branchRepository.findByCode("CN2").orElseGet(()->{
            Branch branch=new Branch();branch.setCode("CN2");branch.setName("Chi nhánh 2");
            return branchRepository.save(branch);
        });
        createIfMissing("manager02", "Quản lý CN2", "BRANCH_MANAGER", "Manager@123", cn2);
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
