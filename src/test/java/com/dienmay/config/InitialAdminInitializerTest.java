package com.dienmay.config;

import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InitialAdminInitializerTest {
    final EmployeeRepository employees=mock(EmployeeRepository.class);
    final RoleRepository roles=mock(RoleRepository.class);
    final BranchRepository branches=mock(BranchRepository.class);
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    InitialAdminInitializer initializer(String password){return new InitialAdminInitializer(employees,roles,branches,encoder,"admin",password);}
    @ParameterizedTest @ValueSource(strings={"","Admin@123","CHANGE_ME-password1!","onlylowercase123456!"})
    void initialize_weakPassword_rejectsWithoutCreatingAccount(String password){
        when(employees.findWithRoleAndBranchByUsername("admin")).thenReturn(Optional.empty());
        assertThatThrownBy(()->initializer(password).run()).isInstanceOf(IllegalStateException.class).hasMessageContaining("INITIAL_ADMIN_PASSWORD");
        verify(employees,never()).save(any());
    }
    @Test void initialize_freshDatabase_createsOnlyAdminWithBcrypt(){
        when(employees.findWithRoleAndBranchByUsername("admin")).thenReturn(Optional.empty());
        when(branches.findByCode("HQ")).thenReturn(Optional.of(new Branch()));when(roles.findByCode("ADMIN")).thenReturn(Optional.of(new Role()));
        initializer("StrongFixtureOnly-10A!").run();
        var saved=ArgumentCaptor.forClass(Employee.class);verify(employees).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin");assertThat(encoder.matches("StrongFixtureOnly-10A!",saved.getValue().getPasswordHash())).isTrue();
        verify(branches,never()).save(any());
    }
    @Test void initialize_existingAdmin_doesNotResetPasswordOrUnlock(){
        var existing=new Employee();var role=mock(Role.class);when(role.getCode()).thenReturn("ADMIN");existing.setRole(role);existing.setActive(false);existing.setPasswordHash("existing-hash");
        when(employees.findWithRoleAndBranchByUsername("admin")).thenReturn(Optional.of(existing));
        initializer("").run();verify(employees,never()).save(any());assertThat(existing.isActive()).isFalse();assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
    }
    @Test void initialize_nameUsedByCashier_rejectsWithoutGrantingAdmin(){
        var existing=new Employee();var role=mock(Role.class);when(role.getCode()).thenReturn("CASHIER");existing.setRole(role);
        when(employees.findWithRoleAndBranchByUsername("admin")).thenReturn(Optional.of(existing));
        assertThatThrownBy(()->initializer("StrongFixtureOnly-10A!").run()).hasMessageContaining("vai trò khác");verify(employees,never()).save(any());
    }
}
