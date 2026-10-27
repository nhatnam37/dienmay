package com.dienmay.support;

import com.dienmay.organization.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Web slice không có DB: khai báo rõ nhân viên fixture còn active.
 * Test thu hồi phiên dùng IT với repository thật, không dùng fixture này. */
public abstract class ActiveEmployeeWebFixture {
    @MockitoBean protected EmployeeRepository activeEmployees;
    @BeforeEach void activeEmployeeFixture() {
        when(activeEmployees.existsByIdAndActiveTrue(anyLong())).thenReturn(true);
    }
}
