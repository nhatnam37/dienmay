package com.dienmay.inventory.security;

import java.util.Set;

// Được tạo từ SecurityContext, KHÔNG bind từ tham số HTTP.
public record InventoryActor(Long employeeId, Long branchId, Set<String> authorities) {
    public InventoryActor { authorities = Set.copyOf(authorities); }
    public boolean hasRole(String role) { return authorities.contains("ROLE_" + role); }
}
