package com.dienmay.inventory.security;

import com.dienmay.common.exception.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class InventoryAccess {
    public void requireInventory(InventoryActor actor) {
        if (actor == null || actor.employeeId() == null || !(actor.hasRole("ADMIN")
                || actor.hasRole("BRANCH_MANAGER") || actor.hasRole("WAREHOUSE"))) {
            throw new AccessDeniedException("Bạn không có quyền quản lý kho.");
        }
    }
    public void requireSupplier(InventoryActor actor) {
        requireInventory(actor);
        if (!actor.hasRole("ADMIN") && !actor.hasRole("WAREHOUSE")) {
            throw new AccessDeniedException("Bạn không có quyền quản lý nhà cung cấp.");
        }
    }
    public Long branch(Long requested, InventoryActor actor, boolean required) {
        requireInventory(actor);
        if (actor.hasRole("ADMIN")) {
            if (required && requested == null) { throw new BusinessException("Vui lòng chọn chi nhánh."); }
            return requested;
        }
        // Kiểm tra tại service, kể cả người dùng tự sửa hidden input/query string.
        if (actor.branchId() == null || (requested != null && !requested.equals(actor.branchId()))) {
            throw new ResourceNotFoundException("Không tìm thấy dữ liệu trong phạm vi chi nhánh.");
        }
        return actor.branchId();
    }
    public void receiptBranch(Long branchId, InventoryActor actor) { branch(branchId, actor, true); }
}
