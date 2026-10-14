package com.dienmay.auth;

import com.dienmay.organization.entity.Employee;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

public class AppUserDetails extends User {

    private final Long employeeId;
    private final Long branchId;
    private final String fullName;

    public AppUserDetails(Employee e) {
        super(e.getUsername(),
                e.getPasswordHash(),
                e.isActive(), // enabled: tài khoản bị khóa sẽ không đăng nhập được
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + e.getRole().getCode())));
        this.employeeId = e.getId();
        this.branchId = e.getBranch().getId();
        this.fullName = e.getFullName();
    }

    public Long getEmployeeId() { return employeeId; }
    public Long getBranchId() { return branchId; }
    public String getFullName() { return fullName; }
}
