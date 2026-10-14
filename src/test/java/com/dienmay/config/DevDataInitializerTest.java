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
    void createsMissingAccountsWithBcryptAndPreservesExistingAccounts() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        Branch hq = new Branch();
        Role admin = new Role();
        Role cashier = new Role();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        when(branches.findByCode("HQ")).thenReturn(Optional.of(hq));
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(admin));
        when(roles.findByCode("CASHIER")).thenReturn(Optional.of(cashier));
        DevDataInitializer initializer = new DevDataInitializer(employees, roles, branches, encoder);

        initializer.run();

        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employees, times(2)).save(saved.capture());
        Employee first = saved.getAllValues().get(0);
        Employee second = saved.getAllValues().get(1);
        assertEquals("admin", first.getUsername());
        assertEquals("cashier01", second.getUsername());
        assertSame(admin, first.getRole());
        assertSame(cashier, second.getRole());
        for (Employee employee : saved.getAllValues()) {
            assertSame(hq, employee.getBranch());
            assertTrue(employee.isActive());
            assertTrue(employee.getPasswordHash().startsWith("$2a$"));
        }
        assertTrue(encoder.matches("Admin@123", first.getPasswordHash()));
        assertTrue(encoder.matches("Cashier@123", second.getPasswordHash()));
        assertFalse(encoder.matches("sai", first.getPasswordHash()));

        clearInvocations(employees);
        when(employees.existsByUsername("admin")).thenReturn(true);
        when(employees.existsByUsername("cashier01")).thenReturn(true);
        initializer.run();
        verify(employees, never()).save(any());
    }
}
