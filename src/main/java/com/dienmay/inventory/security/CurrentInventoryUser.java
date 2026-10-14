package com.dienmay.inventory.security;

import com.dienmay.auth.AppUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
public class CurrentInventoryUser {
    public InventoryActor get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserDetails user)) {
            throw new AccessDeniedException("Vui lòng đăng nhập bằng tài khoản nhân viên.");
        }
        return new InventoryActor(user.getEmployeeId(), user.getBranchId(), user.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toSet()));
    }
}
