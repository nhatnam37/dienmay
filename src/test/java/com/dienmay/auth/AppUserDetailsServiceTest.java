package com.dienmay.auth;

import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.entity.Role;
import com.dienmay.organization.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AppUserDetailsService service;

    // Role/Branch không có setter cho code/id nên dùng mock.
    private Employee employee(boolean active) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("CASHIER");
        Branch branch = mock(Branch.class);
        when(branch.getId()).thenReturn(1L);

        Employee e = new Employee();
        e.setUsername("cashier01");
        e.setPasswordHash("$2a$10$hash");
        e.setFullName("Thu ngân 01");
        e.setRole(role);
        e.setBranch(branch);
        e.setActive(active);
        return e;
    }

    // TC-AUTH-01: tài khoản hợp lệ có đúng thông tin và quyền.
    @Test
    void loadUser_whenExists_returnsUserWithRoleAuthority() {
        Employee employee = employee(true);
        when(employeeRepository.findWithRoleAndBranchByUsername("cashier01"))
                .thenReturn(Optional.of(employee));

        UserDetails user = service.loadUserByUsername("cashier01");

        assertThat(user.getUsername()).isEqualTo("cashier01");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CASHIER");
        assertThat(((AppUserDetails) user).getBranchId()).isEqualTo(1L);
        assertThat(((AppUserDetails) user).getFullName()).isEqualTo("Thu ngân 01");
    }

    // TC-AUTH-02: tài khoản không tồn tại.
    @Test
    void loadUser_whenNotFound_throwsUsernameNotFound() {
        when(employeeRepository.findWithRoleAndBranchByUsername("khongco"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("khongco"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // TC-AUTH-03: tài khoản bị khóa được đánh dấu disabled.
    @Test
    void loadUser_whenInactive_returnsDisabledUser() {
        Employee employee = employee(false);
        when(employeeRepository.findWithRoleAndBranchByUsername("cashier01"))
                .thenReturn(Optional.of(employee));

        UserDetails user = service.loadUserByUsername("cashier01");

        // Spring Security từ chối đăng nhập khi enabled = false.
        assertThat(user.isEnabled()).isFalse();
    }
}
