package com.dienmay.inventory.service;

import com.dienmay.inventory.context.*;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class InventoryContextValidator {
    private final EmployeeRepository employees;
    public InventoryContextValidator(EmployeeRepository employees) { this.employees = employees; }

    public void validate(InventoryContext context) {
        if (context == null || context.refType() == null || context.refType().isBlank()
                || context.refType().length() > 30 || context.refId() == null || context.refId() <= 0) {
            throw new BusinessException("Thông tin tham chiếu kho không hợp lệ.");
        }
        if (context.actorType() == null) { throw new BusinessException("Thiếu loại người thao tác."); }
        if (context.actorType() == ActorType.SYSTEM) {
            if (context.actorId() != null) { throw new BusinessException("Tác vụ hệ thống không được có actorId."); }
            return;
        }
        if (context.actorId() == null || context.actorId() <= 0) {
            throw new BusinessException("Người thao tác phải có mã hợp lệ.");
        }
        if (context.actorType() == ActorType.EMPLOYEE && !employees.existsById(context.actorId())) {
            throw new BusinessException("Không tìm thấy nhân viên thao tác.");
        }
        // TODO: kiểm tra CUSTOMER tồn tại khi triển khai module customer.
    }
}
