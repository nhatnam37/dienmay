package com.dienmay.demo;

import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Bootstrap nhân viên demo; các module hiện chưa có EmployeeService CRUD. Không tạo nhân viên bằng SQL. */
@Component @Profile("demo & !prod & !dev & !test & !perf")
public class DemoAccounts {
    private final EmployeeRepository employees;private final RoleRepository roles;private final BranchRepository branches;private final PasswordEncoder passwords;
    public DemoAccounts(EmployeeRepository employees,RoleRepository roles,BranchRepository branches,PasswordEncoder passwords){this.employees=employees;this.roles=roles;this.branches=branches;this.passwords=passwords;}
    @Transactional public long ensure(List<Long> ids){
        ensure("admin","Quản trị viên Demo An","ADMIN","Admin@123",ids.getFirst());
        for(int i=0;i<3;i++){
            String n="%02d".formatted(i+1);
            ensure("manager"+n,"Quản lý Demo "+n,"BRANCH_MANAGER","Manager@123",ids.get(i));
            ensure("cashier"+n,"Thu ngân Demo "+n,"CASHIER","Cashier@123",ids.get(i));
            ensure("warehouse"+n,"Thủ kho Demo "+n,"WAREHOUSE","Warehouse@123",ids.get(i));
            ensure("warranty"+n,"Bảo hành Demo "+n,"WARRANTY_STAFF","Warranty@123",ids.get(i));
        }
        return 13;
    }
    private void ensure(String username,String name,String role,String password,Long branch){
        Employee employee=employees.findWithRoleAndBranchByUsername(username).orElseGet(Employee::new);
        if(employee.getId()!=null&&!employee.getRole().getCode().equals(role))throw new IllegalStateException("Tài khoản demo trùng tên nhưng khác vai trò: "+username);
        if(employee.getId()==null)employee.setPasswordHash(passwords.encode(password));
        employee.setUsername(username);employee.setFullName(name);employee.setRole(roles.findByCode(role).orElseThrow());employee.setBranch(branches.findById(branch).orElseThrow());employees.saveAndFlush(employee);
    }
}
