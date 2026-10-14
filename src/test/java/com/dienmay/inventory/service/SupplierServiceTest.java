package com.dienmay.inventory.service;

import com.dienmay.inventory.dto.SupplierForm;
import com.dienmay.inventory.entity.Supplier;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SupplierServiceTest {
    SupplierRepository suppliers=mock(SupplierRepository.class);
    GoodsReceiptRepository receipts=mock(GoodsReceiptRepository.class);
    static ValidatorFactory factory;
    SupplierService service;
    InventoryActor actor=new InventoryActor(1L,1L,Set.of("ROLE_WAREHOUSE"));
    @BeforeAll static void open(){factory=Validation.buildDefaultValidatorFactory();}
    @AfterAll static void close(){factory.close();}
    @BeforeEach void setUp(){service=new SupplierService(suppliers,receipts,new InventoryAccess(),factory.getValidator());}
    @Test void delete_supplierHasReceipt_deactivatesWithoutDeletingHistory(){
        Supplier supplier=new Supplier();when(suppliers.findForUpdate(1L)).thenReturn(Optional.of(supplier));when(receipts.existsBySupplierId(1L)).thenReturn(true);
        assertThat(service.delete(1L,actor)).contains("ngừng hoạt động");assertThat(supplier.isActive()).isFalse();verify(suppliers,never()).delete(any());
    }
    @Test void delete_unusedSupplier_deletesRow(){
        Supplier supplier=new Supplier();when(suppliers.findForUpdate(1L)).thenReturn(Optional.of(supplier));service.delete(1L,actor);verify(suppliers).delete(supplier);
    }
    @Test void create_duplicateName_rejectsRequest(){
        SupplierForm form=new SupplierForm();form.setName(" ABC ");when(suppliers.existsByName("ABC")).thenReturn(true);
        assertThatThrownBy(() -> service.create(form,actor)).isInstanceOf(BusinessException.class);
    }
    @Test void getForm_branchManager_isForbidden(){
        assertThatThrownBy(() -> service.getForm(1L,new InventoryActor(2L,1L,Set.of("ROLE_BRANCH_MANAGER")))).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(suppliers);
    }
}
