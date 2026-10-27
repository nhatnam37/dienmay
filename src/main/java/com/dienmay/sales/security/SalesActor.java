package com.dienmay.sales.security;

import com.dienmay.auth.AppUserDetails;
import java.util.Set;
import java.util.stream.Collectors;

// Tạo từ principal phía server, không bind từ form người dùng.
public record SalesActor(Long employeeId,Long branchId,Set<String> authorities) {
    public SalesActor { authorities=Set.copyOf(authorities); }
    public boolean hasRole(String role){return authorities.contains("ROLE_"+role);}
    public static SalesActor from(AppUserDetails principal){return new SalesActor(principal.getEmployeeId(),principal.getBranchId(),principal.getAuthorities().stream().map(a->a.getAuthority()).collect(Collectors.toSet()));}
}
