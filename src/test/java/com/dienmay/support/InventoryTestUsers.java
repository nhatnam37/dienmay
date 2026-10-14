package com.dienmay.support;

import com.dienmay.auth.AppUserDetails;
import com.dienmay.organization.entity.*;
import org.springframework.test.util.ReflectionTestUtils;

public final class InventoryTestUsers {
    private InventoryTestUsers() { }
    public static AppUserDetails user(String roleCode, long branchId, long employeeId) {
        Role role=new Role();ReflectionTestUtils.setField(role,"code",roleCode);
        Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",branchId);
        Employee employee=new Employee();ReflectionTestUtils.setField(employee,"id",employeeId);
        employee.setRole(role);employee.setBranch(branch);employee.setUsername("test-"+roleCode);
        employee.setFullName("Nhân viên kiểm thử");employee.setPasswordHash("hash");
        return new AppUserDetails(employee);
    }
}
