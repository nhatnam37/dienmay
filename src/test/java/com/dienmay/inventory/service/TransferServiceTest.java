package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock GoodsTransferRepository transfers;
    @Mock GoodsTransferItemRepository items;
    @Mock SerialItemRepository serials;
    @Mock SerialStateService serialState;
    @Mock StockService stock;
    @Mock ProductRepository products;
    @Mock BranchRepository branches;
    @Mock EmployeeRepository employees;
    @Mock InventoryProductQueries choices;
    TransferService service;
    final InventoryActor source = new InventoryActor(1L,1L,Set.of("ROLE_WAREHOUSE"));
    final InventoryActor destination = new InventoryActor(2L,2L,Set.of("ROLE_BRANCH_MANAGER"));
    Branch branch(long id) { Branch b = new Branch(); ReflectionTestUtils.setField(b,"id",id); return b; }
    Product product(long id, boolean serial) { Product p = new Product(); ReflectionTestUtils.setField(p,"id",id); p.setTracksSerial(serial); p.setSku("SKU-"+id); return p; }
    SerialItem serial(long id, SerialStatus status) {
        SerialItem s = new SerialItem(); ReflectionTestUtils.setField(s,"id",id); ReflectionTestUtils.setField(s,"status",status);
        s.setSerialNo("SERIAL-"+id); s.setBranch(branch(1)); s.setProduct(product(9,true)); return s;
    }
    GoodsTransfer transfer(TransferStatus status) {
        GoodsTransfer t = new GoodsTransfer(); ReflectionTestUtils.setField(t,"id",7L); t.setFromBranch(branch(1)); t.setToBranch(branch(2)); t.setStatus(status); return t;
    }
    GoodsTransferItem item(SerialItem serial) { GoodsTransferItem i=new GoodsTransferItem();i.setProduct(serial.getProduct());i.setSerialItem(serial);i.setQuantity(1);return i; }
    @BeforeEach void setup() { service = new TransferService(transfers,items,serials,new SerialNumberRules(),serialState,stock,products,branches,employees,new InventoryAccess(),choices); }
    @ParameterizedTest
    @CsvSource({"ship,SHIPPED","ship,RECEIVED","ship,CANCELLED","receive,DRAFT","receive,RECEIVED","receive,CANCELLED","cancel,SHIPPED","cancel,RECEIVED","cancel,CANCELLED"})
    void action_invalidStatus_rejectsWithoutChangingInventory(String action,TransferStatus status) {
        when(transfers.findForUpdate(7L)).thenReturn(Optional.of(transfer(status)));
        assertThatThrownBy(() -> {switch(action){case "ship" -> service.ship(7L,source);case "receive" -> service.receive(7L,destination);default -> service.cancel(7L,source);}})
            .isInstanceOf(BusinessException.class).hasMessageContaining("Không thể");
        verifyNoInteractions(stock,serialState,items);
    }
    @Test void receive_wrongBranch_returnsNotFound() {
        when(transfers.findForUpdate(7L)).thenReturn(Optional.of(transfer(TransferStatus.SHIPPED)));
        assertThatThrownBy(() -> service.receive(7L,source)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(stock,serialState,items);
    }
    @Test void ship_destinationWarehouse_returnsNotFound() {
        when(transfers.findForUpdate(7L)).thenReturn(Optional.of(transfer(TransferStatus.DRAFT)));
        assertThatThrownBy(() -> service.ship(7L,destination)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void cancel_draft_changesOnlyHeader() {
        GoodsTransfer t=transfer(TransferStatus.DRAFT);when(transfers.findForUpdate(7L)).thenReturn(Optional.of(t));
        service.cancel(7L,source);assertThat(t.getStatus()).isEqualTo(TransferStatus.CANCELLED);verifyNoInteractions(stock,serialState);
    }
    @Test void ship_reverseInput_locksSerialsInIncreasingIdOrder() {
        GoodsTransfer t=transfer(TransferStatus.DRAFT);when(transfers.findForUpdate(7L)).thenReturn(Optional.of(t));
        SerialItem a=serial(10,SerialStatus.IN_STOCK),b=serial(20,SerialStatus.IN_STOCK);
        when(items.findAllByTransferIdOrderByIdAsc(7L)).thenReturn(List.of(item(b),item(a)));
        when(products.findForUpdate(9L)).thenReturn(Optional.of(a.getProduct()));
        when(serials.findByIdForUpdate(10L)).thenReturn(Optional.of(a));when(serials.findByIdForUpdate(20L)).thenReturn(Optional.of(b));
        when(employees.findById(1L)).thenReturn(Optional.of(new Employee()));
        service.ship(7L,source);
        InOrder order=inOrder(serials);order.verify(serials).findByIdForUpdate(10L);order.verify(serials).findByIdForUpdate(20L);
        assertThat(t.getStatus()).isEqualTo(TransferStatus.SHIPPED);verify(serialState,times(2)).transition(any(),eq(SerialStatus.IN_TRANSFER),any());
    }
    @Test void ship_oneSerialChanged_rejectsWholeBatchBeforeTransitions() {
        when(transfers.findForUpdate(7L)).thenReturn(Optional.of(transfer(TransferStatus.DRAFT)));
        SerialItem a=serial(10,SerialStatus.IN_STOCK),b=serial(20,SerialStatus.RESERVED);
        when(items.findAllByTransferIdOrderByIdAsc(7L)).thenReturn(List.of(item(a),item(b)));
        when(products.findForUpdate(9L)).thenReturn(Optional.of(a.getProduct()));
        when(serials.findByIdForUpdate(10L)).thenReturn(Optional.of(a));when(serials.findByIdForUpdate(20L)).thenReturn(Optional.of(b));
        when(employees.findById(1L)).thenReturn(Optional.of(new Employee()));
        assertThatThrownBy(() -> service.ship(7L,source)).isInstanceOf(BusinessException.class).hasMessageContaining("SERIAL-20");verifyNoInteractions(stock,serialState);
    }
    TransferForm draft() { TransferForm f=new TransferForm();f.setFromBranchId(1L);f.setToBranchId(2L);f.setSerialText(" serial-10 ");return f; }
    void draftHeader() { when(branches.findById(1L)).thenReturn(Optional.of(branch(1)));when(branches.findById(2L)).thenReturn(Optional.of(branch(2)));when(employees.findById(1L)).thenReturn(Optional.of(new Employee())); }
    @Test void createDraft_validSerial_doesNotReserveOrLockStock() {
        draftHeader();SerialItem s=serial(10,SerialStatus.IN_STOCK);when(serials.findAllBySerialNoIn(anyCollection())).thenReturn(List.of(s));
        when(transfers.saveAndFlush(any())).thenAnswer(call->{GoodsTransfer t=call.getArgument(0);ReflectionTestUtils.setField(t,"id",7L);return t;});
        assertThat(service.createDraft(draft(),source)).isEqualTo(7L);
        verify(serials,never()).findByIdForUpdate(any());verifyNoInteractions(serialState,stock);
        ArgumentCaptor<GoodsTransferItem> cap=ArgumentCaptor.forClass(GoodsTransferItem.class);verify(items).save(cap.capture());assertThat(cap.getValue().getQuantity()).isEqualTo(1);
    }
    @Test void createDraft_sameBranch_rejects() {
        when(branches.findById(1L)).thenReturn(Optional.of(branch(1)));TransferForm f=draft();f.setToBranchId(1L);
        assertThatThrownBy(()->service.createDraft(f,source)).isInstanceOf(BusinessException.class).hasMessageContaining("phải khác nhau");
    }
    @Test void createDraft_otherSourceBranch_returnsNotFound() {
        TransferForm f=draft();f.setFromBranchId(2L);assertThatThrownBy(()->service.createDraft(f,source)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(transfers);
    }
    @Test void createDraft_duplicateSerial_rejectsWithoutSaving() {
        draftHeader();when(serials.findAllBySerialNoIn(anyCollection())).thenReturn(List.of(serial(10,SerialStatus.IN_STOCK)));
        TransferForm f=draft();f.setSerialText("serial-10,SERIAL-10");assertThatThrownBy(()->service.createDraft(f,source)).isInstanceOf(TransferValidationException.class).hasMessageContaining("bị lặp");verify(transfers,never()).saveAndFlush(any());
    }
    @Test void createDraft_unavailableSerial_rejects() {
        draftHeader();when(serials.findAllBySerialNoIn(anyCollection())).thenReturn(List.of(serial(10,SerialStatus.IN_TRANSFER)));
        assertThatThrownBy(()->service.createDraft(draft(),source)).isInstanceOf(BusinessException.class).hasMessageContaining("không IN_STOCK");
    }
    @Test void createDraft_insufficientQuantity_rejects() {
        draftHeader();TransferForm f=draft();f.setSerialText(null);TransferQuantityForm line=new TransferQuantityForm();line.setProductId(9L);line.setQuantity("3");f.setLines(List.of(line));
        when(products.findById(9L)).thenReturn(Optional.of(product(9,false)));when(stock.getAvailableQuantity(1L,9L)).thenReturn(2);
        assertThatThrownBy(()->service.createDraft(f,source)).isInstanceOf(BusinessException.class).hasMessageContaining("Không đủ", "SKU-9");verify(transfers,never()).saveAndFlush(any());
    }
    @Test void ship_cashier_rejectsBeforeLoadingTransfer() {
        assertThatThrownBy(()->service.ship(7L,new InventoryActor(3L,1L,Set.of("ROLE_CASHIER")))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(transfers);
    }
}
