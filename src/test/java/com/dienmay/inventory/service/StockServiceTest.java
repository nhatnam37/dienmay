package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    private final com.dienmay.customer.repository.CustomerRepository customers = mock(com.dienmay.customer.repository.CustomerRepository.class);
    @Mock ProductRepository products;
    @Mock BranchRepository branches;
    @Mock StockQuantityRepository stocks;
    @Mock StockMovementRepository movements;
    @Mock StockRowInitializer initializer;
    @Mock EmployeeRepository employees;
    private StockService service;
    private final StockContext ctx = new StockContext("RECEIPT", 1L, ActorType.SYSTEM, null, null);

    @BeforeEach
    void setUp() {
        service = new StockService(products, branches, stocks, movements, initializer, new InventoryContextValidator(employees, customers));
    }

    private StockQuantity stock(int quantity, int reserved) {
        Product product = new Product();
        when(products.findTracksSerialById(2L)).thenReturn(Optional.of(false));
        when(products.findForUpdate(2L)).thenReturn(Optional.of(product));
        when(branches.findById(1L)).thenReturn(Optional.of(new Branch()));
        StockQuantity row = new StockQuantity(); row.setQuantity(quantity); row.setReservedQty(reserved);
        when(stocks.findLocked(1L, 2L)).thenReturn(Optional.of(row));
        return row;
    }

    @Test
    @com.dienmay.support.TestCase("TC-INV-11")
    void decrease_moreThanAvailable_rejectsWithoutMovement() {
        StockQuantity row = stock(10, 8);
        assertThatThrownBy(() -> service.decrease(1L, 2L, 3, ctx)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("tồn khả dụng");
        assertThat(row.getQuantity()).isEqualTo(10);
        verifyNoInteractions(movements);
    }

    @Test
    void increase_serialProduct_rejectsQuantityStock() {
        when(products.findTracksSerialById(2L)).thenReturn(Optional.of(true));
        assertThatThrownBy(() -> service.increase(1L, 2L, 1, ctx)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Serial");
        verifyNoInteractions(stocks, initializer, movements);
    }

    @Test
    void decrease_validAmount_recordsNegativeDelta() {
        StockQuantity row = stock(10, 2);
        assertThat(service.decrease(1L, 2L, 3, ctx)).isEqualTo(7);
        assertThat(row.getReservedQty()).isEqualTo(2);
        verify(movements).save(argThat(movement -> movement.getDelta() == -3
                && movement.getActorType() == ActorType.SYSTEM && movement.getActorId() == null));
    }

    @Test
    void increase_validAmount_recordsPositiveDelta() {
        stock(10, 2);
        assertThat(service.increase(1L, 2L, 5, ctx)).isEqualTo(15);
        verify(movements).save(argThat(movement -> movement.getDelta() == 5));
    }

    @Test
    void getAvailableQuantity_reservedStock_returnsQuantityMinusReserved() {
        when(products.findById(2L)).thenReturn(Optional.of(new Product()));
        when(branches.findById(1L)).thenReturn(Optional.of(new Branch()));
        StockQuantity row = new StockQuantity(); row.setQuantity(10); row.setReservedQty(3);
        when(stocks.findByBranchIdAndProductId(1L, 2L)).thenReturn(Optional.of(row));
        assertThat(service.getQuantity(1L, 2L)).isEqualTo(10);
        assertThat(service.getAvailableQuantity(1L, 2L)).isEqualTo(7);
    }

    @Test
    void increase_nonPositiveAmount_rejectsRequest() {
        assertThatThrownBy(() -> service.increase(1L, 2L, 0, ctx)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(products, stocks, movements);
    }
}
