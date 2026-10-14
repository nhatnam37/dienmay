package com.dienmay.inventory.service;

import com.dienmay.inventory.context.*;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.organization.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class InventoryContextValidatorTest {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final InventoryContextValidator validator = new InventoryContextValidator(employees);

    @Test
    void validate_existingEmployee_acceptsActor() {
        when(employees.existsById(1L)).thenReturn(true);
        validator.validate(new StockContext("RECEIPT", 1L, ActorType.EMPLOYEE, 1L, null));
        verify(employees).existsById(1L);
    }

    @Test
    void validate_missingEmployee_rejectsActor() {
        assertThatThrownBy(() -> validator.validate(new StockContext("RECEIPT", 1L, ActorType.EMPLOYEE, 9L, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("nhân viên");
    }

    @Test
    void validate_systemWithoutId_acceptsActor() {
        validator.validate(new StockContext("EXPIRY", 1L, ActorType.SYSTEM, null, null));
        verifyNoInteractions(employees);
    }

    @Test
    void validate_systemWithId_rejectsActor() {
        assertThatThrownBy(() -> validator.validate(new StockContext("EXPIRY", 1L, ActorType.SYSTEM, 1L, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("actorId");
    }

    @Test
    void validate_customerWithId_defersExistenceCheck() {
        validator.validate(new StockContext("ONLINE_ORDER", 1L, ActorType.CUSTOMER, 99L, null));
        verifyNoInteractions(employees);
    }

    @Test
    void validate_customerWithoutId_rejectsActor() {
        assertThatThrownBy(() -> validator.validate(new StockContext("ONLINE_ORDER", 1L, ActorType.CUSTOMER, null, null)))
                .isInstanceOf(BusinessException.class);
    }
}
