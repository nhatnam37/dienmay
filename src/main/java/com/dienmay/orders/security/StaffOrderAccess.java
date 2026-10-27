package com.dienmay.orders.security;
import com.dienmay.sales.security.*;
import com.dienmay.organization.entity.Employee;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;
@Component public class StaffOrderAccess {
    private final SalesAccess sales;
    public StaffOrderAccess(SalesAccess sales){this.sales=sales;}
    public Employee validate(SalesActor actor){return sales.validate(actor==null?null:actor.branchId(),actor).employee();}
    public void branch(Employee employee,Long branch){if(!employee.getRole().getCode().equals("ADMIN")&&!employee.getBranch().getId().equals(branch))throw new ResourceNotFoundException("Không tìm thấy đơn hàng.");}
    public Long filterBranch(Employee employee,Long requested){return employee.getRole().getCode().equals("ADMIN")?requested:employee.getBranch().getId();}
}
