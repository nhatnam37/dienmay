package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.security.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceTest {
    @Mock GoodsReceiptRepository receipts;
    @Mock GoodsReceiptItemRepository items;
    @Mock SupplierRepository suppliers;
    @Mock ProductRepository products;
    @Mock SerialItemRepository serials;
    @Mock SerialStateService serialState;
    @Mock StockService stock;
    @Mock BranchRepository branches;
    @Mock EmployeeRepository employees;
    GoodsReceiptService service;
    final InventoryActor actor = new InventoryActor(1L, 1L, Set.of("ROLE_WAREHOUSE"));

    @BeforeEach
    void setUp() {
        service = new GoodsReceiptService(receipts,items,suppliers,products,serials,serialState,
                new SerialNumberRules(),stock,branches,employees,new InventoryAccess(),java.time.Clock.fixed(java.time.Instant.parse("2026-10-23T02:00:00Z"),java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }
    Product product(long id, boolean serial, SerialType type) {
        Product product = new Product(); ReflectionTestUtils.setField(product,"id",id);
        product.setTracksSerial(serial); product.setSerialType(type); product.setStatus(ProductStatus.ACTIVE); return product;
    }
    void prepare(Product... selected) {
        Branch branch = new Branch(); ReflectionTestUtils.setField(branch,"id",1L);
        when(branches.findById(1L)).thenReturn(Optional.of(branch));
        when(suppliers.findForUpdate(10L)).thenReturn(Optional.of(new Supplier()));
        when(employees.findById(1L)).thenReturn(Optional.of(new Employee()));
        when(products.findAllLockedByIdIn(anyCollection())).thenReturn(Arrays.asList(selected));
    }
    ReceiptLineForm line(long id, String numbers) {
        ReceiptLineForm line = new ReceiptLineForm(); line.setProductId(id); line.setSerialText(numbers);
        line.setQuantity("3"); line.setUnitCost("10.00"); return line;
    }
    GoodsReceiptForm form(ReceiptLineForm... lines) {
        GoodsReceiptForm form = new GoodsReceiptForm(); form.setBranchId(1L);form.setSupplierId(10L);
        form.setLines(new ArrayList<>(Arrays.asList(lines)));return form;
    }
    void receiptSave() {
        when(receipts.saveAndFlush(any())).thenAnswer(call -> { GoodsReceipt receipt=call.getArgument(0);
            ReflectionTestUtils.setField(receipt,"id",77L);return receipt; });
    }
    @ParameterizedTest
    @ValueSource(strings={"490154203237519","49015420323751","4901542032375180","49015420323751A","490154 203237518"})
    @com.dienmay.support.TestCase("TC-INV-04")
    @com.dienmay.support.TestCase("TC-INV-05")
    void create_invalidImei_reportsLineAndDoesNotSave(String number) {
        prepare(product(1,true,SerialType.IMEI));
        assertThatThrownBy(() -> service.create(form(line(1,number)),actor)).isInstanceOf(ReceiptValidationException.class).hasMessageContaining("Dòng 1");
        verify(receipts,never()).saveAndFlush(any());verifyNoInteractions(items,stock,serialState);
    }
    @Test
    @com.dienmay.support.TestCase("TC-INV-06")
    void create_duplicateSerialAcrossLines_reportsOriginalLine() {
        prepare(product(1,true,SerialType.GENERIC),product(2,true,SerialType.GENERIC));
        assertThatThrownBy(() -> service.create(form(line(1,"abc-123"),line(2," ABC-123 ")),actor))
                .isInstanceOf(ReceiptValidationException.class).hasMessageContaining("Dòng 2", "trùng dòng 1");
        verify(serials,times(1)).findAllBySerialNoIn(anyCollection());verify(receipts,never()).saveAndFlush(any());
    }
    @Test
    @com.dienmay.support.TestCase("TC-INV-06")
    void create_duplicateSerialWithinLine_rejectsBatch() {
        prepare(product(1,true,SerialType.GENERIC));
        assertThatThrownBy(() -> service.create(form(line(1,"ABC-123,abc-123")),actor))
                .isInstanceOf(ReceiptValidationException.class).hasMessageContaining("trùng dòng 1");
    }
    @Test
    @com.dienmay.support.TestCase("TC-INV-07")
    void create_serialExistsInDatabase_checksWholeBatchWithOneQuery() {
        prepare(product(1,true,SerialType.GENERIC));
        SerialItem existing=new SerialItem();existing.setSerialNo("ABC-123");
        when(serials.findAllBySerialNoIn(anyCollection())).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.create(form(line(1,"ABC-123,DEF-456")),actor))
                .isInstanceOf(ReceiptValidationException.class).hasMessageContaining("đã tồn tại");
        verify(serials).findAllBySerialNoIn(argThat(numbers -> numbers.size()==2));verify(serials,never()).existsBySerialNo(anyString());
    }
    @Test
    @com.dienmay.support.TestCase("TC-INV-08")
    void create_multipleErrors_reportsAllRowsTogether() {
        prepare(product(1,true,SerialType.IMEI),product(2,false,null));
        ReceiptLineForm invalid=line(2,null);invalid.setQuantity("-1");invalid.setUnitCost("bad");
        try { service.create(form(line(1,"490154203237519"),invalid),actor);fail("Phải báo lỗi"); }
        catch(ReceiptValidationException exception) {
            assertThat(exception.getErrors()).anyMatch(error -> error.contains("Dòng 1") && error.contains("Luhn"))
                    .anyMatch(error -> error.contains("Dòng 2") && error.contains("đơn giá"))
                    .anyMatch(error -> error.contains("Dòng 2") && error.contains("số lượng"));
        }
        verifyNoInteractions(items,stock,serialState);verify(receipts,never()).saveAndFlush(any());
    }
    @Test
    void create_discontinuedProduct_rejectsReceipt() {
        Product product=product(1,false,null);product.setStatus(ProductStatus.DISCONTINUED);prepare(product);
        assertThatThrownBy(() -> service.create(form(line(1,null)),actor)).isInstanceOf(ReceiptValidationException.class).hasMessageContaining("ngừng kinh doanh");
    }
    @Test
    void create_warehouseRequestsAnotherBranch_returnsNotFoundBeforeSaving() {
        GoodsReceiptForm form=form(line(1,null));form.setBranchId(2L);
        assertThatThrownBy(() -> service.create(form,actor)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(receipts,products,suppliers,stock,serialState);
    }
    @Test
    void create_moreThanFiveHundredSerials_rejectsRow() {
        prepare(product(1,true,SerialType.GENERIC));
        String input=IntStream.range(0,501).mapToObj(i -> "SER-"+i).collect(java.util.stream.Collectors.joining(","));
        assertThatThrownBy(() -> service.create(form(line(1,input)),actor)).isInstanceOf(ReceiptValidationException.class).hasMessageContaining("500 Serial");
    }
    @Test
    void create_validSerials_ignoresTypedQuantityAndUsesBatchRegistration() {
        prepare(product(1,true,SerialType.GENERIC));receiptSave();
        ReceiptLineForm line=line(1," abc-123,\n def-456 ");line.setQuantity("not-a-number");
        assertThat(service.create(form(line),actor)).isEqualTo(77L);
        verify(items).save(argThat(item -> item.getQuantity()==2));
        verify(serialState,times(2)).register(eq(1L),eq(1L),eq(77L),anyString(),eq(new BigDecimal("10.00")),
                argThat(context -> context.refType().equals("GOODS_RECEIPT")),eq(true));
        verifyNoInteractions(stock);
    }
    @Test
    void create_validQuantity_increasesStockAndGeneratesFinalCode() {
        prepare(product(1,false,null));receiptSave();
        service.create(form(line(1,null)),actor);
        verify(stock).increase(eq(1L),eq(1L),eq(3),argThat(context -> context.refId()==77L));
        verify(receipts).saveAndFlush(argThat(receipt -> receipt.getCode().matches("PN-[0-9]{8}-00077")));
    }
    @Test
    void create_repeatedProduct_reportsOriginalRow() {
        prepare(product(1,false,null));
        assertThatThrownBy(() -> service.create(form(line(1,null),line(1,null)),actor)).isInstanceOf(ReceiptValidationException.class).hasMessageContaining("sản phẩm trùng dòng 1");
    }
    @Test
    @com.dienmay.support.TestCase("TC-INV-16")
    void getById_receiptFromAnotherBranch_returnsNotFound() {
        Branch other=new Branch();ReflectionTestUtils.setField(other,"id",2L);
        GoodsReceipt receipt=new GoodsReceipt();receipt.setBranch(other);
        when(receipts.findDetail(5L)).thenReturn(Optional.of(receipt));
        assertThatThrownBy(() -> service.getById(5L,actor)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(items,serials);
    }
    @ParameterizedTest
    @ValueSource(strings={"ABCD","ABCDEFGHIJKLMNOPQRSTUVWXYZ12345","ABC_12","ABC 123",""})
    void create_invalidGenericSerial_reportsLineWithoutSaving(String raw) {
        prepare(product(1,true,SerialType.GENERIC));
        assertThatThrownBy(() -> service.create(form(line(1,raw)),actor)).isInstanceOf(ReceiptValidationException.class).hasMessageContaining("Dòng 1");
        verify(receipts,never()).saveAndFlush(any());
    }
    @Test
    void create_branchManagerRequestsAnotherBranch_returnsNotFound() {
        GoodsReceiptForm form=form(line(1,null));form.setBranchId(2L);
        assertThatThrownBy(() -> service.create(form,new InventoryActor(1L,1L,Set.of("ROLE_BRANCH_MANAGER"))))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(receipts,products,suppliers,stock,serialState);
    }
}
