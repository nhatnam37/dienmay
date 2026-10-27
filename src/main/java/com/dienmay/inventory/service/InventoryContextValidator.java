package com.dienmay.inventory.service;

import com.dienmay.inventory.context.*;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class InventoryContextValidator {
    private final EmployeeRepository employees;
    private final com.dienmay.customer.repository.CustomerRepository customers;
    public InventoryContextValidator(EmployeeRepository employees, com.dienmay.customer.repository.CustomerRepository customers) {
        this.employees = employees; this.customers = customers;
    }

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
        if (context.actorType() == ActorType.CUSTOMER && !customers.existsById(context.actorId())) {
            throw new BusinessException("Không tìm thấy khách hàng thao tác.");
        }
    }
}
