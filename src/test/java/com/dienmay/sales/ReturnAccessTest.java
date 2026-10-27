package com.dienmay.sales;
import com.dienmay.sales.security.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ReturnAccessTest {
    EmployeeRepository employees=mock(EmployeeRepository.class);ReturnAccess access=new ReturnAccess(employees);
    Employee employee(String role){Employee e=new Employee();Role r=new Role();ReflectionTestUtils.setField(r,"code",role);Branch b=new Branch();ReflectionTestUtils.setField(b,"id",1L);e.setRole(r);e.setBranch(b);return e;}
    @Test void validate_cashier_isRejected(){when(employees.findSalesActor(1L)).thenReturn(Optional.of(employee("CASHIER")));assertThatThrownBy(()->access.validate(new SalesActor(1L,1L,Set.of("ROLE_CASHIER")))).isInstanceOf(AccessDeniedException.class);}
    @Test void branch_managerOtherBranch_isRejected(){assertThatThrownBy(()->access.branch(employee("BRANCH_MANAGER"),2L)).isInstanceOf(ResourceNotFoundException.class);}
    @Test void branch_adminOtherBranch_isAllowed(){access.branch(employee("ADMIN"),2L);}
}
