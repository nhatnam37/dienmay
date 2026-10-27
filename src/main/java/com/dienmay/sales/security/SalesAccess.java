package com.dienmay.sales.security;

import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class SalesAccess {
    private final EmployeeRepository employees;
    public SalesAccess(EmployeeRepository employees){this.employees=employees;}
    public record Scope(Employee employee,boolean privileged) { }
    public Scope validate(Long branchId,SalesActor actor){
        if(actor==null||actor.employeeId()==null||actor.branchId()==null||!actor.branchId().equals(branchId)
                ||!(actor.hasRole("CASHIER")||actor.hasRole("BRANCH_MANAGER")||actor.hasRole("ADMIN"))){throw denied();}
        Employee employee=employees.findSalesActor(actor.employeeId()).orElseThrow(this::denied);
        String role=employee.getRole().getCode();
        // Kiểm tra lại tài khoản/role/chi nhánh ở DB, không dùng principal cũ để nâng quyền giảm giá.
        if(!employee.isActive()||!employee.getBranch().isActive()||!employee.getBranch().getId().equals(branchId)
                ||!Set.of("CASHIER","BRANCH_MANAGER","ADMIN").contains(role)||!actor.hasRole(role)){throw denied();}
        return new Scope(employee,!role.equals("CASHIER"));
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Bạn chỉ được bán hàng bằng tài khoản còn hoạt động tại chi nhánh của mình.");}
}
