package com.dienmay.inventory.service;

import com.dienmay.inventory.context.ActorType;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.GoodsReceipt;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.InventoryActor;
import com.dienmay.inventory.serial.*;
import com.dienmay.catalog.entity.Product;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SerialLookupServiceTest {
    @Mock SerialItemRepository serials;
    @Mock SerialHistoryRepository histories;
    @Mock EmployeeRepository employees;
    @Mock GoodsReceiptRepository receipts;
    @Mock GoodsTransferRepository transfers;
    SerialLookupService service(){return new SerialLookupService(serials,histories,employees,receipts,transfers,new SerialNumberRules());}
    @Test void lookup_cashier_rejectsBeforeQuery(){
        assertThatThrownBy(()->service().lookup("ABC-123",new InventoryActor(1L,1L,Set.of("ROLE_CASHIER")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(serials,histories);
    }
    @Test void lookup_systemAndCustomerHistory_returnsSafeActorNamesAcrossBranches(){
        Product product=new Product();product.setSku("SKU");product.setName("Máy");
        Branch branch=mock(Branch.class);when(branch.getName()).thenReturn("Chi nhánh khác");
        GoodsReceipt receipt=new GoodsReceipt();ReflectionTestUtils.setField(receipt,"id",7L);receipt.setCode("PN-00007");
        SerialItem serial=new SerialItem();ReflectionTestUtils.setField(serial,"id",10L);serial.setSerialNo("ABC-123");serial.setProduct(product);serial.setBranch(branch);serial.setReceipt(receipt);
        when(serials.findLookupBySerialNo("ABC-123")).thenReturn(Optional.of(serial));
        SerialHistory system=new SerialHistory();system.setActorType(ActorType.SYSTEM);system.setRefType("EXPIRY");system.setRefId(1L);system.setToStatus(SerialStatus.IN_STOCK);
        SerialHistory customer=new SerialHistory();customer.setActorType(ActorType.CUSTOMER);customer.setActorId(99L);customer.setRefType("ORDER");customer.setRefId(2L);customer.setToStatus(SerialStatus.RESERVED);
        when(histories.findAllBySerialItemIdOrderByChangedAtAscIdAsc(10L)).thenReturn(List.of(system,customer));
        SerialLookupView view=service().lookup(" abc-123 ",new InventoryActor(1L,999L,Set.of("ROLE_WARRANTY_STAFF")));
        assertThat(view.branchName()).isEqualTo("Chi nhánh khác");assertThat(view.history()).extracting(SerialTimelineEntry::actorName).containsExactly("Hệ thống","Khách hàng #99");
        assertThat(view.history()).allMatch(event->event.documentUrl()==null);verify(employees,times(1)).findAllById(any());
    }
}
