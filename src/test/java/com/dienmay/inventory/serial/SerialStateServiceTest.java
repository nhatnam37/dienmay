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
                new SerialNumberRules(), new InventoryContextValidator(employees));
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

    @Test
    void transition_invalidTransition_doesNotWriteHistory() {
        SerialItem item = item(SerialStatus.RESERVED);
        assertThatThrownBy(() -> service.transition(item, SerialStatus.DAMAGED, system))
                .isInstanceOf(InvalidSerialTransitionException.class).hasMessageContaining("RESERVED", "DAMAGED");
        assertThat(item.getStatus()).isEqualTo(SerialStatus.RESERVED);
        verifyNoInteractions(histories);
        verify(serials, never()).save(any());
    }

    @Test
    void transition_damagedSerial_cannotLeaveFinalState() {
        assertThatThrownBy(() -> service.transition(item(SerialStatus.DAMAGED), SerialStatus.IN_STOCK, system))
                .isInstanceOf(InvalidSerialTransitionException.class);
        verifyNoInteractions(histories);
    }

    @Test
    void transition_transferCompleted_changesBranchAndRecordsBothBranches() {
        SerialItem item = item(SerialStatus.IN_TRANSFER);
        Branch destination = branch(3);
        when(branches.findById(3L)).thenReturn(Optional.of(destination));
        service.transition(item, SerialStatus.IN_STOCK, new TransitionContext("TRANSFER", 1L, ActorType.SYSTEM, null, null, 3L));
        assertThat(item.getBranch()).isSameAs(destination);
        verify(histories).save(argThat(history -> history.getFromBranch().getId().equals(2L)
                && history.getToBranch().getId().equals(3L)));
    }

    @Test
    void transition_branchChangeOutsideTransfer_rejectsRequest() {
        SerialItem item = item(SerialStatus.IN_STOCK);
        assertThatThrownBy(() -> service.transition(item, SerialStatus.SOLD,
                new TransitionContext("ORDER", 1L, ActorType.SYSTEM, null, null, 3L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đổi chi nhánh");
        verifyNoInteractions(histories);
    }

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
}
