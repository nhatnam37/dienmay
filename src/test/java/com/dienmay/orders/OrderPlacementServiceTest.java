package com.dienmay.orders;

import com.dienmay.orders.service.*;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.config.OrderProperties;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.customer.repository.CustomerRepository;
import com.dienmay.customer.entity.Customer;
import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.SerialStateService;
import com.dienmay.inventory.service.StockService;
import com.dienmay.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderPlacementServiceTest {
    static ValidatorFactory factory;
    OrderRepository orders;OrderItemRepository items;OrderItemSerialRepository links;OrderStatusHistoryRepository history;
    CustomerRepository customers;BranchRepository branches;ProductRepository products;OrderStockQueries quantities;SerialItemRepository serials;SerialStateService states;StockService stock;OrderQueryService views;OrderPlacementService service;
    Product product;Branch branch;Customer customer;Clock clock=Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeAll static void init(){factory=Validation.buildDefaultValidatorFactory();}
    @AfterAll static void close(){factory.close();}
    @BeforeEach void setup(){
        orders=mock(OrderRepository.class);items=mock(OrderItemRepository.class);links=mock(OrderItemSerialRepository.class);history=mock(OrderStatusHistoryRepository.class);customers=mock(CustomerRepository.class);branches=mock(BranchRepository.class);products=mock(ProductRepository.class);quantities=mock(OrderStockQueries.class);serials=mock(SerialItemRepository.class);states=mock(SerialStateService.class);stock=mock(StockService.class);views=mock(OrderQueryService.class);
        service=new OrderPlacementService(new OrderRequests(factory.getValidator(),new ObjectMapper()),mock(OrderPlacementGuard.class),orders,items,links,history,customers,branches,products,quantities,serials,states,stock,views,new OrderProperties(),clock);
        customer=new Customer();ReflectionTestUtils.setField(customer,"id",1L);branch=new Branch();ReflectionTestUtils.setField(branch,"id",2L);product=new Product();ReflectionTestUtils.setField(product,"id",3L);product.setName("Phụ kiện");product.setPrice(new BigDecimal("100"));product.setStatus(ProductStatus.ACTIVE);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));when(branches.findById(2L)).thenReturn(Optional.of(branch));when(products.findForUpdate(3L)).thenReturn(Optional.of(product));when(quantities.available(2L,3L)).thenReturn(5);
        when(orders.saveAndFlush(any())).thenAnswer(c->{var o=c.getArgument(0,CustomerOrder.class);ReflectionTestUtils.setField(o,"id",10L);return o;});when(items.saveAndFlush(any())).thenAnswer(c->c.getArgument(0));
    }
    PlaceOrderRequest request(DeliveryType delivery,OrderPaymentMethod payment,String address,List<OrderLineRequest> lines){return new PlaceOrderRequest(UUID.randomUUID().toString(),2L,delivery,payment,"Người nhận","+84901234567",address,null,lines);}
    PlaceOrderRequest valid(){return request(DeliveryType.PICKUP,OrderPaymentMethod.PAY_AT_STORE,null,List.of(new OrderLineRequest(3L,2)));}
    @org.junit.jupiter.api.DisplayName("Giỏ rỗng bị từ chối")
    @Test void place_emptyCart_rejected(){assertThatThrownBy(()->service.place(request(DeliveryType.PICKUP,OrderPaymentMethod.ONLINE,null,List.of()),new CustomerActor(1L))).isInstanceOf(BusinessException.class);verifyNoInteractions(stock,states);}
    @org.junit.jupiter.api.DisplayName("Khách ngừng hoạt động không được đặt hàng")
    @Test void place_inactiveCustomer_rejected(){customer.setActive(false);assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("Khách hàng");}
    @org.junit.jupiter.api.DisplayName("Chi nhánh ngừng hoạt động không được nhận đơn")
    @Test void place_inactiveBranch_rejected(){branch.setActive(false);assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("Chi nhánh");}
    @org.junit.jupiter.api.DisplayName("Giao hàng cần địa chỉ")
    @Test @com.dienmay.support.TestCase("TC-WEB-14")
    void place_deliveryWithoutAddress_rejected(){assertThatThrownBy(()->service.place(request(DeliveryType.DELIVERY,OrderPaymentMethod.COD," ",valid().items()),new CustomerActor(1L))).hasMessageContaining("địa chỉ");}
    @org.junit.jupiter.api.DisplayName("Nhận tại quầy không được chọn thu hộ")
    @Test @com.dienmay.support.TestCase("TC-WEB-28")
    void place_pickupWithCod_rejected(){assertThatThrownBy(()->service.place(request(DeliveryType.PICKUP,OrderPaymentMethod.COD,null,valid().items()),new CustomerActor(1L))).hasMessageContaining("không phù hợp");}
    @org.junit.jupiter.api.DisplayName("Giao hàng không được chọn thanh toán tại quầy")
    @Test @com.dienmay.support.TestCase("TC-WEB-28")
    void place_deliveryWithPayAtStore_rejected(){assertThatThrownBy(()->service.place(request(DeliveryType.DELIVERY,OrderPaymentMethod.PAY_AT_STORE,"Hà Nội",valid().items()),new CustomerActor(1L))).hasMessageContaining("không phù hợp");}
    @org.junit.jupiter.api.DisplayName("Số điện thoại sai bị từ chối")
    @Test void place_invalidPhone_rejected(){var f=valid();assertThatThrownBy(()->service.place(new PlaceOrderRequest(f.placeToken(),2L,f.deliveryType(),f.paymentMethod(),f.receiverName(),"123",null,null,f.items()),new CustomerActor(1L))).hasMessageContaining("điện thoại");}
    @org.junit.jupiter.api.DisplayName("Thiếu tồn không giữ hàng một phần")
    @Test @com.dienmay.support.TestCase("TC-WEB-11")
    void place_shortStock_rejectsWithoutPartialReservation(){when(quantities.available(2L,3L)).thenReturn(1);assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("cần 2, còn 1");verifyNoInteractions(stock,states);verify(orders,never()).saveAndFlush(any());}
    @org.junit.jupiter.api.DisplayName("Sản phẩm ngừng kinh doanh không được đặt")
    @Test void place_discontinued_rejected(){product.setStatus(ProductStatus.DISCONTINUED);assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("ngừng kinh doanh");}
    @org.junit.jupiter.api.DisplayName("Giá có phần lẻ đồng không được đặt")
    @Test void place_fractionalPrice_rejected(){product.setPrice(new BigDecimal("100.50"));assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("nguyên đồng");}
    @org.junit.jupiter.api.DisplayName("Tổng đơn bằng không bị từ chối")
    @Test void place_zeroTotal_rejected(){product.setPrice(BigDecimal.ZERO);assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("lớn hơn 0");}
    @org.junit.jupiter.api.DisplayName("Đơn hợp lệ dùng giá server và hạn giữ hàng")
    @Test @com.dienmay.support.TestCase("TC-WEB-10")
    @com.dienmay.support.TestCase("TC-WEB-12")
    void place_valid_usesServerPriceAndPlacedExpiry(){service.place(valid(),new CustomerActor(1L));verify(orders).saveAndFlush(argThat(o->o.getSubtotal().compareTo(new BigDecimal("200"))==0&&o.getStatus()==OrderStatus.PLACED&&o.getExpiresAt().equals(LocalDateTime.now(clock).plusHours(24))));verify(stock).reserve(eq(2L),eq(3L),eq(2),any());}
    @org.junit.jupiter.api.DisplayName("Thanh toán online ban đầu giữ hàng mười lăm phút")
    @Test @com.dienmay.support.TestCase("TC-WEB-10")
    void place_online_initialHoldIs15Minutes(){service.place(request(DeliveryType.PICKUP,OrderPaymentMethod.ONLINE,null,valid().items()),new CustomerActor(1L));verify(orders).saveAndFlush(argThat(o->o.getStatus()==OrderStatus.PENDING_PAYMENT&&o.getExpiresAt().equals(LocalDateTime.now(clock).plusMinutes(15))));}
    @org.junit.jupiter.api.DisplayName("Thiếu người thao tác bị từ chối")
    @Test void place_missingActor_rejected(){assertThatThrownBy(()->service.place(valid(),null)).hasMessageContaining("đăng nhập");}
    @org.junit.jupiter.api.DisplayName("Token đặt hàng sai bị từ chối")
    @Test void place_invalidToken_rejected(){var f=valid();assertThatThrownBy(()->service.place(new PlaceOrderRequest("not-a-uuid",2L,f.deliveryType(),f.paymentMethod(),f.receiverName(),f.receiverPhone(),null,null,f.items()),new CustomerActor(1L))).hasMessageContaining("Token");}
    @org.junit.jupiter.api.DisplayName("Dòng sản phẩm trùng bị từ chối")
    @Test void place_duplicateProduct_rejected(){assertThatThrownBy(()->service.place(request(DeliveryType.PICKUP,OrderPaymentMethod.ONLINE,null,List.of(new OrderLineRequest(3L,1),new OrderLineRequest(3L,1))),new CustomerActor(1L))).hasMessageContaining("một dòng");}
    @org.junit.jupiter.api.DisplayName("Số lượng ngoài giới hạn bị từ chối")
    @Test void place_invalidQuantity_rejected(){assertThatThrownBy(()->service.place(request(DeliveryType.PICKUP,OrderPaymentMethod.ONLINE,null,List.of(new OrderLineRequest(3L,0))),new CustomerActor(1L))).isInstanceOf(BusinessException.class);}
    @org.junit.jupiter.api.DisplayName("Sản phẩm không tồn tại bị từ chối")
    @Test void place_missingProduct_rejected(){when(products.findForUpdate(3L)).thenReturn(Optional.empty());assertThatThrownBy(()->service.place(valid(),new CustomerActor(1L))).hasMessageContaining("Không tìm thấy sản phẩm");}
    @org.junit.jupiter.api.DisplayName("Gom toàn bộ lỗi thiếu hàng và không giữ hàng một phần")
    @Test @com.dienmay.support.TestCase("TC-WEB-11")
    void place_severalShortProducts_reportsAllWithoutHoldingAnything(){
        Product other=new Product();ReflectionTestUtils.setField(other,"id",4L);other.setName("Máy");other.setPrice(new BigDecimal("500"));other.setStatus(ProductStatus.ACTIVE);other.setTracksSerial(true);when(products.findForUpdate(4L)).thenReturn(Optional.of(other));when(quantities.available(2L,3L)).thenReturn(0);when(quantities.fifoIds(2L,4L,1)).thenReturn(List.of());
        assertThatThrownBy(()->service.place(request(DeliveryType.PICKUP,OrderPaymentMethod.ONLINE,null,List.of(new OrderLineRequest(3L,1),new OrderLineRequest(4L,1))),new CustomerActor(1L))).hasMessageContaining("Phụ kiện: cần 1, còn 0").hasMessageContaining("Máy: cần 1, còn 0");verifyNoInteractions(stock,states);
    }

}
