package com.dienmay.reports;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Set;
@Component
public class ReportAccess {
    private final EmployeeRepository employees;
    public ReportAccess(EmployeeRepository employees){this.employees=employees;}
    public enum Kind { FINANCIAL, INVENTORY, CHAIN }
    public ReportScope resolve(ReportScope requested,SalesActor actor,Kind kind){
        if(actor==null||actor.employeeId()==null)throw denied();
        var employee=employees.findSalesActor(actor.employeeId()).orElseThrow(this::denied);
        String role=employee.getRole().getCode();
        if(!employee.isActive()||!employee.getBranch().isActive()||!employee.getBranch().getId().equals(actor.branchId())||!actor.hasRole(role))throw denied();
        if(!Set.of("ADMIN","BRANCH_MANAGER","WAREHOUSE").contains(role))throw denied();
        if(kind==Kind.CHAIN&&!role.equals("ADMIN"))throw denied();
        if(kind==Kind.FINANCIAL&&role.equals("WAREHOUSE"))throw denied();
        if(role.equals("ADMIN"))return requested==null?ReportScope.chain():requested;
        if(requested!=null&&requested.branchId()!=null&&!employee.getBranch().getId().equals(requested.branchId()))
            throw new ResourceNotFoundException("Không tìm thấy báo cáo tại chi nhánh này.");
        return ReportScope.branch(employee.getBranch().getId());
    }
    private AccessDeniedException denied(){return new AccessDeniedException("Bạn không có quyền xem loại báo cáo này.");}
}
