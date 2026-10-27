package com.dienmay.sales;

import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.organization.entity.*;
import com.dienmay.sales.config.PosProperties;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.mapper.CheckoutMapper;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.security.*;
import com.dienmay.sales.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {
    @Mock InvoiceRepository invoices;@Mock InvoiceItemRepository items;@Mock PaymentRepository payments;@Mock CheckoutGuard guard;
    @Mock ProductRepository products;@Mock SaleSerialQueries saleSerials;@Mock SerialItemRepository serials;@Mock SerialStateService states;
    @Mock StockService stock;@Mock SalesWarrantyIssuer warranties;@Mock SalesAccess access;
    ValidatorFactory factory;CheckoutService service;CheckoutNormalizer normalizer;Employee employee;
    SalesActor actor=new SalesActor(1L,1L,Set.of("ROLE_CASHIER"));
    @BeforeEach void setup(){factory=Validation.buildDefaultValidatorFactory();normalizer=new CheckoutNormalizer(new ObjectMapper());service=new CheckoutService(invoices,items,payments,guard,products,saleSerials,serials,access,normalizer,new CheckoutMapper(),new SerialNumberRules(),factory.getValidator(),new PosProperties(),Clock.fixed(Instant.parse("2026-01-31T03:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh")),new InvoiceWriter(invoices,items,payments,states,stock,warranties,new CheckoutMapper()));Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);branch.setName("HQ");employee=new Employee();ReflectionTestUtils.setField(employee,"id",1L);employee.setBranch(branch);employee.setFullName("Thu ngân");}
    @AfterEach void close(){factory.close();}
    void authorize(boolean privileged){when(invoices.findByCheckoutToken(anyString())).thenReturn(Optional.empty());when(access.validate(eq(1L),eq(actor))).thenReturn(new SalesAccess.Scope(employee,privileged));}
    Product product(long id,boolean serial){Product product=new Product();ReflectionTestUtils.setField(product,"id",id);product.setSku("SKU-"+id);product.setName("Máy");product.setPrice(new BigDecimal("100"));product.setTracksSerial(serial);product.setSerialType(serial?SerialType.GENERIC:null);product.setWarrantyMonths(serial?12:0);when(products.findForUpdate(id)).thenReturn(Optional.of(product));return product;}
    CheckoutRequest request(List<CheckoutLineRequest> cart,String phone,String discount,List<CheckoutPaymentRequest> paid){return new CheckoutRequest(UUID.randomUUID().toString(),1L,"Khách",phone,new BigDecimal(discount),null,cart,paid);}
    List<CheckoutPaymentRequest> cash(String amount,String received){return List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal(amount),received==null?null:new BigDecimal(received),null));}
    CheckoutLineRequest line(boolean serial){return new CheckoutLineRequest(2L,serial?"serial-001":null,1);}
    CheckoutRequest basic(boolean serial){return request(List.of(line(serial)),serial?"0901234567":null,"0",cash("100","200"));}
    SerialItem prepareSerial(Product product,long id,String number,SerialStatus status,long branch){SerialItem item=new SerialItem();ReflectionTestUtils.setField(item,"id",id);ReflectionTestUtils.setField(item,"status",status);item.setProduct(product);item.setSerialNo(number);Branch b=new Branch();ReflectionTestUtils.setField(b,"id",branch);item.setBranch(b);when(serials.findByIdForUpdate(id)).thenReturn(Optional.of(item));return item;}
    void discover(long id){when(saleSerials.findIdentities(anyCollection())).thenReturn(List.of(new SaleSerialIdentity(id,"SERIAL-001",2L)));}
    void saving(){when(invoices.saveAndFlush(any())).thenAnswer(call->{Invoice i=call.getArgument(0);ReflectionTestUtils.setField(i,"id",7L);return i;});when(items.save(any())).thenAnswer(call->{InvoiceItem i=call.getArgument(0);ReflectionTestUtils.setField(i,"id",8L);return i;});when(payments.save(any())).thenAnswer(call->call.getArgument(0));}
    @org.junit.jupiter.api.DisplayName("Giỏ rỗng bị từ chối")
    @Test @com.dienmay.support.TestCase("TC-POS-02")
    void checkout_emptyCart_rejects(){authorize(false);assertThatThrownBy(()->service.checkout(request(List.of(),null,"0",cash("100","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Giỏ hàng");verify(invoices,never()).saveAndFlush(any());}
    @org.junit.jupiter.api.DisplayName("Bán Serial phải có số điện thoại")
    @Test @com.dienmay.support.TestCase("TC-POS-03")
    void checkout_serialWithoutPhone_rejects(){authorize(false);product(2,true);assertThatThrownBy(()->service.checkout(request(List.of(line(true)),null,"0",cash("100","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("số điện thoại");verifyNoInteractions(states);}
    @org.junit.jupiter.api.DisplayName("Số điện thoại sai bị từ chối")
    @Test void checkout_invalidPhone_rejects(){authorize(false);assertThatThrownBy(()->service.checkout(request(List.of(line(true)),"0123456789","0",cash("100","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("điện thoại");}
    @org.junit.jupiter.api.DisplayName("Serial trùng bị từ chối trước khi khóa")
    @Test @com.dienmay.support.TestCase("TC-POS-04")
    void checkout_duplicateSerial_rejectsBeforeLocking(){authorize(false);product(2,true);assertThatThrownBy(()->service.checkout(request(List.of(line(true),new CheckoutLineRequest(2L," SERIAL-001 ",1)),"0901234567","0",cash("200","200")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("trùng");verifyNoInteractions(serials,states);}
    @org.junit.jupiter.api.DisplayName("Sản phẩm ngừng kinh doanh không được bán")
    @Test @com.dienmay.support.TestCase("TC-POS-06")
    void checkout_discontinuedProduct_rejects(){authorize(false);Product p=product(2,false);p.setStatus(ProductStatus.DISCONTINUED);assertThatThrownBy(()->service.checkout(basic(false),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("ngừng kinh doanh");}
    @org.junit.jupiter.api.DisplayName("Serial chi nhánh khác không được bán")
    @Test @com.dienmay.support.TestCase("TC-POS-05")
    void checkout_serialOtherBranch_rejects(){authorize(false);Product p=product(2,true);discover(10);prepareSerial(p,10,"SERIAL-001",SerialStatus.IN_STOCK,2);assertThatThrownBy(()->service.checkout(basic(true),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("SERIAL-001","chi nhánh khác");verifyNoInteractions(states);}
    @org.junit.jupiter.api.DisplayName("Serial đã giữ không được bán tại quầy")
    @Test @com.dienmay.support.TestCase("TC-POS-05")
    void checkout_reservedSerial_rejects(){authorize(false);Product p=product(2,true);discover(10);prepareSerial(p,10,"SERIAL-001",SerialStatus.RESERVED,1);assertThatThrownBy(()->service.checkout(basic(true),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("SERIAL-001","RESERVED");}
    @org.junit.jupiter.api.DisplayName("Serial không tồn tại bị từ chối")
    @Test void checkout_serialMissingFromDatabase_rejects(){authorize(false);product(2,true);when(saleSerials.findIdentities(anyCollection())).thenReturn(List.of());assertThatThrownBy(()->service.checkout(basic(true),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Không tìm thấy Serial");}
    @org.junit.jupiter.api.DisplayName("Mỗi dòng Serial chỉ có số lượng một")
    @Test void checkout_serialQuantityTwo_rejects(){authorize(false);product(2,true);assertThatThrownBy(()->service.checkout(request(List.of(new CheckoutLineRequest(2L,"SERIAL-001",2)),"0901234567","0",cash("200","200")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("bằng 1");}
    @org.junit.jupiter.api.DisplayName("Số lượng bán bằng không bị từ chối")
    @Test void checkout_quantityZero_rejects(){authorize(false);assertThatThrownBy(()->service.checkout(request(List.of(new CheckoutLineRequest(2L,null,0)),null,"0",cash("100","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Số lượng");}
    @org.junit.jupiter.api.DisplayName("Thu ngân không được giảm quá năm phần trăm")
    @Test @com.dienmay.support.TestCase("TC-POS-07")
    void checkout_cashierDiscountAboveFivePercent_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"6",cash("94","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("5%");}
    @org.junit.jupiter.api.DisplayName("Thu ngân được giảm đúng giới hạn")
    @Test void checkout_cashierDiscountAtLimit_accepts(){authorize(false);product(2,false);saving();var view=service.checkout(request(List.of(line(false)),null,"5",cash("95","100")),actor);assertThat(view.totalAmount()).isEqualByComparingTo("95");}
    @org.junit.jupiter.api.DisplayName("Quản lý được giảm vượt mức thu ngân")
    @Test @com.dienmay.support.TestCase("TC-POS-08")
    void checkout_managerDiscountAboveCashierLimit_accepts(){authorize(true);product(2,false);saving();var view=service.checkout(request(List.of(line(false)),null,"20",cash("80","100")),actor);assertThat(view.totalAmount()).isEqualByComparingTo("80");}
    @org.junit.jupiter.api.DisplayName("Không được giảm một trăm phần trăm")
    @Test void checkout_managerDiscountOneHundredPercent_rejects(){authorize(true);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"100",cash("1","1")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("100%");}
    @org.junit.jupiter.api.DisplayName("Tổng thanh toán thiếu bị từ chối")
    @Test @com.dienmay.support.TestCase("TC-POS-09")
    void checkout_paymentBelowTotal_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",cash("99","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Tổng thanh toán");}
    @org.junit.jupiter.api.DisplayName("Tổng thanh toán vượt hóa đơn bị từ chối")
    @Test void checkout_paymentAboveTotal_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",cash("101","101")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Tổng thanh toán");}
    @org.junit.jupiter.api.DisplayName("Tiền mặt khách đưa thiếu bị từ chối")
    @Test void checkout_cashReceivedBelowCashPart_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",cash("100","99")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Tiền mặt nhận");}
    @org.junit.jupiter.api.DisplayName("Tiền thối được tính lại ở server")
    @Test @com.dienmay.support.TestCase("TC-POS-10")
    void checkout_cashOverpayment_calculatesChangeOnServer(){authorize(false);product(2,false);saving();var view=service.checkout(basic(false),actor);assertThat(view.payments().getFirst().changeGiven()).isEqualByComparingTo("100");assertThat(view.code()).isEqualTo("HD-20260131-00007");verify(stock).decrease(eq(1L),eq(2L),eq(1),argThat(ctx->ctx.refType().equals("INVOICE")));}
    @org.junit.jupiter.api.DisplayName("Thanh toán thẻ không có tiền mặt khách đưa")
    @Test void checkout_cardWithCashReceived_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",List.of(new CheckoutPaymentRequest(PaymentMethod.CARD,new BigDecimal("100"),BigDecimal.ZERO,"REF"))),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("Thẻ/chuyển khoản");}
    @org.junit.jupiter.api.DisplayName("Nhiều phương thức chỉ tính tiền thối trên phần tiền mặt")
    @Test @com.dienmay.support.TestCase("TC-POS-10")
    void checkout_splitPayments_calculatesOnlyCashChange(){authorize(false);product(2,false);saving();var view=service.checkout(request(List.of(line(false)),null,"0",List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal("40"),new BigDecimal("50"),null),new CheckoutPaymentRequest(PaymentMethod.TRANSFER,new BigDecimal("60"),null,"TX-1"))),actor);assertThat(view.payments().getFirst().changeGiven()).isEqualByComparingTo("10");assertThat(view.payments().getLast().changeGiven()).isNull();}
    @org.junit.jupiter.api.DisplayName("Hóa đơn phải có dòng thanh toán")
    @Test void checkout_noPayments_rejects(){authorize(false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",List.of()),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("ít nhất một dòng");}
    @org.junit.jupiter.api.DisplayName("Thành tiền vượt độ dài cột bị từ chối")
    @Test void checkout_serverLineTotalOverflow_rejects(){authorize(false);Product p=product(2,false);p.setPrice(new BigDecimal("9999999999999"));assertThatThrownBy(()->service.checkout(request(List.of(new CheckoutLineRequest(2L,null,2)),null,"0",cash("100","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("DECIMAL");}
    @org.junit.jupiter.api.DisplayName("Khóa Serial theo mã tăng dần dù đầu vào đảo thứ tự")
    @Test void checkout_reversedInput_locksSerialIdsAscending(){authorize(false);Product p=product(2,true);var a=prepareSerial(p,10,"SERIAL-001",SerialStatus.IN_STOCK,1);var z=prepareSerial(p,20,"SERIAL-002",SerialStatus.IN_STOCK,1);when(saleSerials.findIdentities(anyCollection())).thenReturn(List.of(new SaleSerialIdentity(20L,"SERIAL-002",2L),new SaleSerialIdentity(10L,"SERIAL-001",2L)));saving();service.checkout(request(List.of(new CheckoutLineRequest(2L,"SERIAL-002",1),line(true)),"+84901234567","0",cash("200","200")),actor);InOrder order=inOrder(serials);order.verify(serials).findByIdForUpdate(10L);order.verify(serials).findByIdForUpdate(20L);verify(states).transition(eq(a),eq(SerialStatus.SOLD),any());verify(states).transition(eq(z),eq(SerialStatus.SOLD),any());verify(warranties,times(2)).issue(any());}

    @org.junit.jupiter.api.DisplayName("Giá sản phẩm có phần lẻ đồng bị từ chối")
    @Test void checkout_fractionalProductPrice_rejects(){authorize(false);product(2,false).setPrice(new BigDecimal("100.5"));assertThatThrownBy(()->service.checkout(basic(false),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("nguyên đồng");verify(invoices,never()).saveAndFlush(any());}
    @org.junit.jupiter.api.DisplayName("Giảm giá có phần lẻ đồng bị từ chối")
    @Test void checkout_fractionalDiscount_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0.5",cash("100","100")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("nguyên đồng");}
    @org.junit.jupiter.api.DisplayName("Thanh toán có phần lẻ đồng bị từ chối")
    @Test void checkout_fractionalPayment_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",cash("100.5","101")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("nguyên đồng");}
    @org.junit.jupiter.api.DisplayName("Tiền khách đưa có phần lẻ đồng bị từ chối")
    @Test void checkout_fractionalCashReceived_rejects(){authorize(false);product(2,false);assertThatThrownBy(()->service.checkout(request(List.of(line(false)),null,"0",cash("100","100.5")),actor)).isInstanceOf(BusinessException.class).hasMessageContaining("nguyên đồng");verify(invoices,never()).saveAndFlush(any());}
}
