package com.dienmay.inventory.serial;

import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.entity.GoodsReceipt;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.service.InventoryContextValidator;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SerialStateServiceTest {
    private final com.dienmay.customer.repository.CustomerRepository customers = mock(com.dienmay.customer.repository.CustomerRepository.class);
    @Mock SerialItemRepository serials;
    @Mock SerialHistoryRepository histories;
    @Mock ProductRepository products;
    @Mock BranchRepository branches;
    @Mock GoodsReceiptRepository receipts;
    @Mock EmployeeRepository employees;
    private SerialStateService service;
    private final TransitionContext system = new TransitionContext("ORDER", 10L, ActorType.SYSTEM, null, "Kiểm thử", null);

    @BeforeEach
    void setUp() {
        service = new SerialStateService(serials, histories, products, branches, receipts,
                new SerialNumberRules(), new InventoryContextValidator(employees, customers));
    }

    private Branch branch(long id) {
        Branch branch = new Branch();
        ReflectionTestUtils.setField(branch, "id", id);
        return branch;
    }

    private SerialItem item(SerialStatus status) {
        SerialItem item = new SerialItem();
        ReflectionTestUtils.setField(item, "id", 1L);
        item.setBranch(branch(2));
        item.setStatus(status); // Test ở cùng package để dựng trạng thái đầu vào.
        when(serials.findByIdForUpdate(1L)).thenReturn(Optional.of(item));
        return item;
    }

    @org.junit.jupiter.api.DisplayName("Chuyển hợp lệ ghi đúng một dòng lịch sử")
    @Test
    void transition_validTransition_writesExactlyOneHistory() {
        SerialItem item = item(SerialStatus.IN_STOCK);
        service.transition(item, SerialStatus.RESERVED, system);
        assertThat(item.getStatus()).isEqualTo(SerialStatus.RESERVED);
        ArgumentCaptor<SerialHistory> saved = ArgumentCaptor.forClass(SerialHistory.class);
        verify(histories, times(1)).save(saved.capture());
        assertThat(saved.getValue().getFromStatus()).isEqualTo(SerialStatus.IN_STOCK);
        assertThat(saved.getValue().getToStatus()).isEqualTo(SerialStatus.RESERVED);
        assertThat(saved.getValue().getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(saved.getValue().getActorId()).isNull();
        assertThat(saved.getValue().getRefId()).isEqualTo(10L);
    }

    @org.junit.jupiter.api.DisplayName("Chuyển sai luồng không được ghi lịch sử")
    @Test
    void transition_invalidTransition_doesNotWriteHistory() {
        SerialItem item = item(SerialStatus.RESERVED);
        assertThatThrownBy(() -> service.transition(item, SerialStatus.DAMAGED, system))
                .isInstanceOf(InvalidSerialTransitionException.class).hasMessageContaining("RESERVED", "DAMAGED");
        assertThat(item.getStatus()).isEqualTo(SerialStatus.RESERVED);
        verifyNoInteractions(histories);
        verify(serials, never()).save(any());
    }

    @org.junit.jupiter.api.DisplayName("Serial hỏng không được rời trạng thái kết thúc")
    @Test
    @com.dienmay.support.TestCase("TC-INV-02")
    void transition_damagedSerial_cannotLeaveFinalState() {
        assertThatThrownBy(() -> service.transition(item(SerialStatus.DAMAGED), SerialStatus.IN_STOCK, system))
                .isInstanceOf(InvalidSerialTransitionException.class);
        verifyNoInteractions(histories);
    }

    @org.junit.jupiter.api.DisplayName("Nhận chuyển kho cập nhật chi nhánh và ghi cả nơi đi nơi đến")
    @Test
    @com.dienmay.support.TestCase("TC-INV-13")
    void transition_transferCompleted_changesBranchAndRecordsBothBranches() {
        SerialItem item = item(SerialStatus.IN_TRANSFER);
        Branch destination = branch(3);
        when(branches.findById(3L)).thenReturn(Optional.of(destination));
        service.transition(item, SerialStatus.IN_STOCK, new TransitionContext("TRANSFER", 1L, ActorType.SYSTEM, null, null, 3L));
        assertThat(item.getBranch()).isSameAs(destination);
        verify(histories).save(argThat(history -> history.getFromBranch().getId().equals(2L)
                && history.getToBranch().getId().equals(3L)));
    }

    @org.junit.jupiter.api.DisplayName("Đổi chi nhánh ngoài luồng hợp lệ bị từ chối")
    @Test
    void transition_branchChangeOutsideTransfer_rejectsRequest() {
        SerialItem item = item(SerialStatus.IN_STOCK);
        assertThatThrownBy(() -> service.transition(item, SerialStatus.SOLD,
                new TransitionContext("ORDER", 1L, ActorType.SYSTEM, null, null, 3L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đổi chi nhánh");
        verifyNoInteractions(histories);
    }

    @org.junit.jupiter.api.DisplayName("Đăng ký Serial chuẩn hóa và ghi lịch sử trong kho")
    @Test
    void register_validSerial_createsInStockWithInitialHistory() {
        Product product = new Product(); product.setTracksSerial(true); product.setSerialType(SerialType.GENERIC);
        Branch branch = branch(2);
        GoodsReceipt receipt = new GoodsReceipt(); receipt.setBranch(branch);
        when(products.findForUpdate(1L)).thenReturn(Optional.of(product));
        when(branches.findById(2L)).thenReturn(Optional.of(branch));
        when(receipts.findById(3L)).thenReturn(Optional.of(receipt));
        when(serials.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        SerialItem created = service.register(1L, 2L, 3L, " abc-123 ", BigDecimal.TEN, system);
        assertThat(created.getSerialNo()).isEqualTo("ABC-123");
        assertThat(created.getStatus()).isEqualTo(SerialStatus.IN_STOCK);
        verify(histories).save(argThat(history -> history.getFromStatus() == null
                && history.getFromBranch() == null && history.getToStatus() == SerialStatus.IN_STOCK));
    }

    Product registerProduct(){Product p=new Product();p.setTracksSerial(true);p.setSerialType(SerialType.GENERIC);when(products.findForUpdate(1L)).thenReturn(Optional.of(p));return p;}
    void registerReceipt(long receiptBranch){when(branches.findById(2L)).thenReturn(Optional.of(branch(2)));GoodsReceipt r=new GoodsReceipt();r.setBranch(branch(receiptBranch));when(receipts.findById(3L)).thenReturn(Optional.of(r));}
    @Test @DisplayName("Không đăng ký Serial cho sản phẩm không quản lý Serial")
    void register_nonSerialProduct_rejectsWithoutHistory(){registerProduct().setTracksSerial(false);assertThatThrownBy(()->service.register(1L,2L,3L,"ABCDE",BigDecimal.TEN,system)).isInstanceOf(BusinessException.class).hasMessageContaining("không bật");verifyNoInteractions(histories,serials);}
    @Test @DisplayName("Serial sai định dạng bị từ chối trước khi lưu")
    void register_invalidNumber_rejectsWithoutHistory(){registerProduct();assertThatThrownBy(()->service.register(1L,2L,3L,"!@#",BigDecimal.TEN,system)).isInstanceOf(BusinessException.class);verifyNoInteractions(histories,serials);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.NullAndEmptySource
    @DisplayName("Serial chưa đăng ký không được chuyển trạng thái")
    void transition_unregisteredSerial_rejectsWithoutHistory(String marker){SerialItem value=marker==null?null:new SerialItem();assertThatThrownBy(()->service.transition(value,SerialStatus.SOLD,system)).isInstanceOf(BusinessException.class).hasMessageContaining("chưa được đăng ký");verifyNoInteractions(serials,histories);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.NullSource @org.junit.jupiter.params.provider.ValueSource(strings={"-1","1.001","10000000000000"})
    @DisplayName("Giá vốn ngoài giới hạn bị từ chối, không ghi Serial hay lịch sử")
    void register_invalidCost_rejectsWithoutSaving(String value){registerProduct();BigDecimal cost=value==null?null:new BigDecimal(value);assertThatThrownBy(()->service.register(1L,2L,3L,"ABCDE",cost,system)).isInstanceOf(BusinessException.class).hasMessageContaining("Giá vốn");verifyNoInteractions(histories,serials,branches,receipts);}
    @Test @DisplayName("Chi nhánh đăng ký phải trùng chi nhánh phiếu nhập")
    void register_receiptOtherBranch_rejectsWithoutSaving(){registerProduct();registerReceipt(4);assertThatThrownBy(()->service.register(1L,2L,3L,"ABCDE",BigDecimal.TEN,system)).isInstanceOf(BusinessException.class).hasMessageContaining("không khớp phiếu nhập");verifyNoInteractions(histories,serials);}
    @Test @DisplayName("Chi nhánh đích context phải trùng chi nhánh đăng ký")
    void register_contextOtherBranch_rejectsWithoutSaving(){registerProduct();registerReceipt(2);var context=new TransitionContext("RECEIPT",3L,ActorType.SYSTEM,null,null,4L);assertThatThrownBy(()->service.register(1L,2L,3L,"ABCDE",BigDecimal.TEN,context)).isInstanceOf(BusinessException.class).hasMessageContaining("Chi nhánh đích");verifyNoInteractions(histories,serials);}
    @Test @DisplayName("Serial trùng sau chuẩn hóa bị từ chối")
    @com.dienmay.support.TestCase("TC-INV-07")
    void register_existingNumber_rejectsBeforeSave(){registerProduct();registerReceipt(2);when(serials.existsBySerialNo("ABCDE")).thenReturn(true);var context=new TransitionContext("RECEIPT",3L,ActorType.SYSTEM,null,null,2L);assertThatThrownBy(()->service.register(1L,2L,3L," abcde ",BigDecimal.TEN,context)).isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");verify(serials,never()).saveAndFlush(any());verifyNoInteractions(histories);}
    @Test @DisplayName("Serial thiếu trạng thái không được ghi lịch sử chuyển")
    void transition_missingStatus_rejectsWithoutHistory(){assertThatThrownBy(()->service.transition(item(null),SerialStatus.SOLD,system)).isInstanceOf(InvalidSerialTransitionException.class);verifyNoInteractions(histories);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.NullSource @org.junit.jupiter.params.provider.ValueSource(strings={"","   "})
    @DisplayName("Trả hàng đổi chi nhánh phải có lý do")
    void transition_salesReturnMissingReason_rejectsBranchChange(String reason){var serial=item(SerialStatus.SOLD);var context=new TransitionContext("SALES_RETURN",3L,ActorType.SYSTEM,null,reason,3L);assertThatThrownBy(()->service.transition(serial,SerialStatus.IN_STOCK,context)).isInstanceOf(BusinessException.class).hasMessageContaining("đổi chi nhánh");assertThat(serial.getBranch().getId()).isEqualTo(2);verifyNoInteractions(histories);}

}
