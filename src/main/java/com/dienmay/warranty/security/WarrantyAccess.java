package com.dienmay.warranty.security;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Set;
@Component
public class WarrantyAccess {
    private final EmployeeRepository employees;
    public WarrantyAccess(EmployeeRepository employees){this.employees=employees;}
    public Employee validate(SalesActor actor){
        if(actor==null || actor.employeeId()==null) throw denied();
        Employee employee=employees.findSalesActor(actor.employeeId()).orElseThrow(this::denied);
        String role=employee.getRole().getCode();
        if(!employee.isActive() || !employee.getBranch().isActive() || !employee.getBranch().getId().equals(actor.branchId())
                || !Set.of("WARRANTY_STAFF","BRANCH_MANAGER","ADMIN").contains(role) || !actor.hasRole(role)) throw denied();
        return employee;
    }
    public void requireBranch(Employee employee,Long branch){
        if(!employee.getRole().getCode().equals("ADMIN") && !employee.getBranch().getId().equals(branch))
            throw new ResourceNotFoundException("Không tìm thấy phiếu bảo hành.");
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Bạn không có quyền xử lý bảo hành.");}
}
