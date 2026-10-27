package com.dienmay.sales.security;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class ReturnAccess {
    private final EmployeeRepository employees;
    public ReturnAccess(EmployeeRepository employees){this.employees=employees;}
    public Employee validate(SalesActor actor){
        if(actor==null||actor.employeeId()==null||actor.branchId()==null)throw denied();
        Employee employee=employees.findSalesActor(actor.employeeId()).orElseThrow(this::denied);String role=employee.getRole().getCode();
        if(!employee.isActive()||!employee.getBranch().isActive()||!employee.getBranch().getId().equals(actor.branchId())||!(role.equals("ADMIN")||role.equals("BRANCH_MANAGER"))||!actor.hasRole(role))throw denied();return employee;
    }
    public void branch(Employee employee,Long branch){if(!employee.getRole().getCode().equals("ADMIN")&&!employee.getBranch().getId().equals(branch))throw new ResourceNotFoundException("Không tìm thấy hóa đơn hoặc phiếu trả.");}
    private AccessDeniedException denied(){return new AccessDeniedException("Chỉ quản lý chi nhánh hoặc ADMIN được hủy/đổi trả.");}
}
