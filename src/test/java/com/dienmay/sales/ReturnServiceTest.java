package com.dienmay.sales;
import com.dienmay.sales.config.PosProperties;
import com.dienmay.sales.dto.ReturnForm;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.security.*;
import com.dienmay.sales.service.*;
import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.organization.entity.*;
import com.dienmay.warranty.repository.WarrantyRepository;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {
    @Mock InvoiceRepository invoices;@Mock InvoiceItemRepository items;@Mock SalesReturnRepository returns;@Mock SalesReturnItemRepository returned;@Mock PaymentRepository payments;@Mock PaymentRefundRepository refunds;@Mock ProductRepository products;@Mock SerialItemRepository serials;@Mock SerialStateService states;@Mock StockService stock;@Mock WarrantyRepository warranties;@Mock ReturnAccess access;
    ReturnService service;ValidatorFactory factory;PosProperties properties=new PosProperties();SalesActor actor=new SalesActor(1L,1L,Set.of("ROLE_BRANCH_MANAGER"));Invoice invoice;Employee employee;
    @BeforeEach void setup(){factory=Validation.buildDefaultValidatorFactory();service=new ReturnService(invoices,items,returns,returned,payments,refunds,products,serials,states,stock,warranties,access,properties,new RefundMath(),factory.getValidator(),Clock.fixed(Instant.parse("2026-10-15T05:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh")));}
    @AfterEach void close(){factory.close();}
    void authorized(int daysAgo){employee=new Employee();ReflectionTestUtils.setField(employee,"id",1L);when(access.validate(actor)).thenReturn(employee);invoice=new Invoice();ReflectionTestUtils.setField(invoice,"id",7L);invoice.setCreatedAt(LocalDate.of(2026,10,15).minusDays(daysAgo).atStartOfDay());Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);invoice.setBranch(branch);when(invoices.findForUpdate(7L)).thenReturn(Optional.of(invoice));}
    ReturnForm form(int qty){ReturnForm f=new ReturnForm();f.setReason("Khách trả hàng");ReturnForm.Line l=new ReturnForm.Line();l.setInvoiceItemId(8L);l.setQuantity(qty);f.setLines(List.of(l));return f;}
    InvoiceItem item(){InvoiceItem i=new InvoiceItem();ReflectionTestUtils.setField(i,"id",8L);i.setQuantity(1);i.setUnitPrice(new BigDecimal("100"));Product p=new Product();ReflectionTestUtils.setField(p,"id",3L);i.setProduct(p);when(items.findForReturn(7L)).thenReturn(List.of(i));when(returned.findForInvoice(7L)).thenReturn(List.of());return i;}
    @org.junit.jupiter.api.DisplayName("Quá hạn trả hàng bị từ chối")
    @Test @com.dienmay.support.TestCase("TC-POS-20")
    void returnItems_afterWindow_isRejected(){authorized(8);assertThatThrownBy(()->service.returnItems(7L,form(1),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("thời hạn");verifyNoInteractions(items,returns);}
    @org.junit.jupiter.api.DisplayName("Thiếu lý do bị từ chối")
    @Test @com.dienmay.support.TestCase("TC-POS-20")
    void returnItems_missingReason_isRejected(){var form=form(1);form.setReason(" ");assertThatThrownBy(()->service.returnItems(7L,form,actor)).isInstanceOf(BusinessException.class).hasMessageContaining("lý do");verifyNoInteractions(invoices);}
    @org.junit.jupiter.api.DisplayName("Không được trả quá số lượng bán còn lại")
    @Test void returnItems_moreThanSold_isRejected(){authorized(0);item();assertThatThrownBy(()->service.returnItems(7L,form(2),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("vượt số lượng");verifyNoInteractions(returns,states,stock);}
    @org.junit.jupiter.api.DisplayName("Máy đang bảo hành phải kết thúc sửa trước khi trả")
    @Test @com.dienmay.support.TestCase("TC-POS-21")
    @com.dienmay.support.TestCase("TC-WAR-15")
    void returnItems_serialInWarranty_isRejected(){authorized(0);var item=item();SerialItem serial=new SerialItem();ReflectionTestUtils.setField(serial,"id",9L);ReflectionTestUtils.setField(serial,"serialNo","ABC-123");ReflectionTestUtils.setField(serial,"status",SerialStatus.IN_WARRANTY_REPAIR);item.setSerialItem(serial);when(products.findForUpdate(3L)).thenReturn(Optional.of(item.getProduct()));when(serials.findByIdForUpdate(9L)).thenReturn(Optional.of(serial));assertThatThrownBy(()->service.returnItems(7L,form(1),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("đang bảo hành");verifyNoInteractions(returns,states);}
    @org.junit.jupiter.api.DisplayName("Không hủy hóa đơn khác ngày bán")
    @Test void cancelInvoice_previousDay_isRejected(){authorized(1);assertThatThrownBy(()->service.cancelInvoice(7L,"Khách hủy",actor)).isInstanceOf(BusinessException.class).hasMessageContaining("trong ngày");}
    @org.junit.jupiter.api.DisplayName("Hóa đơn đã trả một phần không được hủy toàn bộ")
    @Test void cancelInvoice_alreadyReturned_isRejected(){authorized(0);when(items.findForReturn(7L)).thenReturn(List.of());when(returned.findForInvoice(7L)).thenReturn(List.of(new SalesReturnItem()));assertThatThrownBy(()->service.cancelInvoice(7L,"Khách hủy",actor)).isInstanceOf(BusinessException.class).hasMessageContaining("đã trả hàng");}
    @org.junit.jupiter.api.DisplayName("Hóa đơn đã hủy không được xử lý lại")
    @Test void cancelInvoice_cancelled_isRejected(){authorized(0);invoice.setStatus(InvoiceStatus.CANCELLED);assertThatThrownBy(()->service.cancelInvoice(7L,"Khách hủy",actor)).isInstanceOf(BusinessException.class).hasMessageContaining("đã hủy");}

    @Test @DisplayName("Thiếu dữ liệu trả hàng phải bị từ chối trước mọi thao tác DB")
    void returnItems_nullForm_rejectsBeforeAccess(){assertThatThrownBy(()->service.returnItems(7L,null,actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Thiếu dữ liệu");verifyNoInteractions(invoices,returns,states,stock);}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.NullAndEmptySource
    @DisplayName("Hủy hóa đơn bắt buộc có lý do")
    @com.dienmay.support.TestCase("TC-POS-20")
    void cancelInvoice_missingReason_rejectsBeforeInvoiceLock(String reason){when(access.validate(actor)).thenReturn(new Employee());assertThatThrownBy(()->service.cancelInvoice(7L,reason,actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Lý do bắt buộc");verifyNoInteractions(invoices,returns);}
    @Test @DisplayName("Lý do hủy vượt 255 ký tự bị từ chối")
    void cancelInvoice_overlongReason_rejectsBeforeInvoiceLock(){when(access.validate(actor)).thenReturn(new Employee());assertThatThrownBy(()->service.cancelInvoice(7L,"a".repeat(256),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("255");verifyNoInteractions(invoices);}
    @Test @DisplayName("Hóa đơn ngày tương lai không được hủy")
    void cancelInvoice_futureSaleDate_rejectsWithoutChanges(){authorized(-1);assertThatThrownBy(()->service.cancelInvoice(7L,"Nhập sai ngày",actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Ngày bán không hợp lệ");verifyNoInteractions(items,returns,states,stock);}
    @Test @DisplayName("Không được trả dòng không thuộc hóa đơn")
    void returnItems_foreignInvoiceLine_rejectsWithoutChanges(){authorized(0);item();var f=form(1);f.getLines().get(0).setInvoiceItemId(99L);assertThatThrownBy(()->service.returnItems(7L,f,actor)).isInstanceOf(BusinessException.class).hasMessageContaining("không thuộc hóa đơn");verifyNoInteractions(returns,states,stock);}
    @Test @DisplayName("Không chọn số lượng trả thì không tạo phiếu trả")
    void returnItems_zeroSelection_rejectsWithoutChanges(){authorized(0);item();assertThatThrownBy(()->service.returnItems(7L,form(0),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("ít nhất một");verifyNoInteractions(returns,states,stock);}
    @Test @DisplayName("Serial đã rời SOLD không thể được trả thêm lần nữa")
    void returnItems_serialNotSold_rejectsWithoutChanges(){authorized(0);var i=item();var serial=new SerialItem();ReflectionTestUtils.setField(serial,"id",9L);ReflectionTestUtils.setField(serial,"status",SerialStatus.IN_STOCK);serial.setSerialNo("ABC");i.setSerialItem(serial);when(products.findForUpdate(3L)).thenReturn(Optional.of(i.getProduct()));when(serials.findByIdForUpdate(9L)).thenReturn(Optional.of(serial));assertThatThrownBy(()->service.returnItems(7L,form(1),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("không ở trạng thái đã bán");verifyNoInteractions(returns,states,stock);}

}
