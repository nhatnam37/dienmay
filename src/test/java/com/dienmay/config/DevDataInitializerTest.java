package com.dienmay.config;

import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.entity.Role;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.organization.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevDataInitializerTest {
    @Test
    @com.dienmay.support.TestCase("TC-AUTH-10")
    void initialize_missingAccounts_createsBcryptAndPreservesExisting() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        Branch hq = new Branch();
        Role admin = new Role();
        Role cashier = new Role();
        Role warranty = new Role();
        Role manager = new Role();
        Branch cn2 = new Branch();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(branches.findByCode("HQ")).thenReturn(Optional.of(hq));
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(admin));
        when(roles.findByCode("CASHIER")).thenReturn(Optional.of(cashier));
        when(roles.findByCode("WARRANTY_STAFF")).thenReturn(Optional.of(warranty));
        when(roles.findByCode("BRANCH_MANAGER")).thenReturn(Optional.of(manager));
        when(branches.findByCode("CN2")).thenReturn(Optional.of(cn2));
        DevDataInitializer initializer = new DevDataInitializer(employees, roles, branches, encoder);

        initializer.run();

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees, times(5)).save(saved.capture());
        Employee first = saved.getAllValues().get(0);
        Employee second = saved.getAllValues().get(1);
        assertEquals("admin", first.getUsername());
        assertEquals("cashier01", second.getUsername());
        assertSame(admin, first.getRole());
        assertSame(cashier, second.getRole());
        Employee third = saved.getAllValues().get(2);
        assertEquals("warranty01", third.getUsername());
        assertSame(warranty, third.getRole());
        assertTrue(encoder.matches("Warranty@123", third.getPasswordHash()));
        for (Employee employee : saved.getAllValues()) {
            assertSame(employee.getUsername().equals("manager02") ? cn2 : hq, employee.getBranch());
            assertTrue(employee.isActive());
            assertTrue(employee.getPasswordHash().startsWith("$2a$"));
        }
        assertTrue(encoder.matches("Admin@123", first.getPasswordHash()));
        assertTrue(encoder.matches("Cashier@123", second.getPasswordHash()));
        assertFalse(encoder.matches("sai", first.getPasswordHash()));
        Employee fourth=saved.getAllValues().get(3),fifth=saved.getAllValues().get(4);
        assertEquals("manager01",fourth.getUsername());assertEquals("manager02",fifth.getUsername());
        assertSame(manager,fourth.getRole());assertSame(manager,fifth.getRole());
        assertTrue(encoder.matches("Manager@123",fourth.getPasswordHash()));assertTrue(encoder.matches("Manager@123",fifth.getPasswordHash()));

        clearInvocations(employees);
        when(employees.existsByUsername("admin")).thenReturn(true);
        when(employees.existsByUsername("cashier01")).thenReturn(true);
        when(employees.existsByUsername("warranty01")).thenReturn(true);
        when(employees.existsByUsername("manager01")).thenReturn(true);
        when(employees.existsByUsername("manager02")).thenReturn(true);
        initializer.run();
        verify(employees, never()).save(any());
    }
    @Test void initialize_missingCn2_createsBranchAndManager(){
        var employees=mock(EmployeeRepository.class);var roles=mock(RoleRepository.class);var branches=mock(BranchRepository.class);var hq=new Branch();
        when(branches.findByCode("HQ")).thenReturn(Optional.of(hq));when(branches.findByCode("CN2")).thenReturn(Optional.empty());when(branches.save(any())).thenAnswer(i->i.getArgument(0));
        when(roles.findByCode(any())).thenReturn(Optional.of(new Role()));
        new DevDataInitializer(employees,roles,branches,new BCryptPasswordEncoder()).run();
        var saved=ArgumentCaptor.forClass(Employee.class);verify(employees,times(5)).save(saved.capture());
        Employee manager=saved.getAllValues().stream().filter(e->e.getUsername().equals("manager02")).findFirst().orElseThrow();
        assertEquals("CN2",manager.getBranch().getCode());assertTrue(manager.getBranch().isActive());
    }

}
