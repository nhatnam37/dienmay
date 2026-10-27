package com.dienmay.sales;

import com.dienmay.sales.security.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SalesAccessTest {
    @Mock EmployeeRepository employees;
    @Test void validate_adminOtherBranch_isForbidden(){assertThatThrownBy(()->new SalesAccess(employees).validate(2L,new SalesActor(1L,1L,Set.of("ROLE_ADMIN")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(employees);}
    @Test void validate_warehouse_isForbidden(){assertThatThrownBy(()->new SalesAccess(employees).validate(1L,new SalesActor(1L,1L,Set.of("ROLE_WAREHOUSE")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void validate_currentDatabaseRole_controlsDiscountPrivilege(){Branch b=new Branch();ReflectionTestUtils.setField(b,"id",1L);Role role=new Role();ReflectionTestUtils.setField(role,"code","CASHIER");Employee e=new Employee();e.setBranch(b);e.setRole(role);when(employees.findSalesActor(1L)).thenReturn(Optional.of(e));var scope=new SalesAccess(employees).validate(1L,new SalesActor(1L,1L,Set.of("ROLE_CASHIER","ROLE_ADMIN")));assertThat(scope.privileged()).isFalse();}
}
