package com.dienmay.orders;
import com.dienmay.orders.service.*;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.security.*;
import com.dienmay.orders.entity.*;
import com.dienmay.sales.security.*;
import com.dienmay.sales.repository.InvoiceRepository;
import com.dienmay.sales.service.InvoiceWriter;
import com.dienmay.organization.entity.*;
import com.dienmay.common.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class StaffOrderServiceTest {
    OrderRepository orders;OrderLifecycleService lifecycle;StaffOrderAccess access;StaffOrderService service;CustomerOrder order;Employee employee;
    Clock clock=Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));SalesActor actor=new SalesActor(1L,1L,Set.of("ROLE_CASHIER"));
    @BeforeEach void setup(){orders=mock(OrderRepository.class);lifecycle=mock(OrderLifecycleService.class);access=mock(StaffOrderAccess.class);service=new StaffOrderService(orders,mock(OrderItemRepository.class),mock(OrderPaymentRepository.class),mock(OrderStatusHistoryRepository.class),mock(InvoiceRepository.class),mock(InvoiceWriter.class),lifecycle,access,new StaffOrderRules(),clock);employee=new Employee();ReflectionTestUtils.setField(employee,"id",1L);when(access.validate(actor)).thenReturn(employee);Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);order=new CustomerOrder();ReflectionTestUtils.setField(order,"id",5L);order.setBranch(branch);order.setVersion(1L);order.setStatus(OrderStatus.PLACED);order.setDeliveryType(DeliveryType.PICKUP);order.setExpiresAt(LocalDateTime.now(clock).plusHours(1));when(orders.findForUpdate(5L)).thenReturn(Optional.of(order));}
    @Test void change_staleVersion_rejected(){assertThatThrownBy(()->service.change(5L,0L,OrderStatus.CONFIRMED,null,null,actor)).hasMessageContaining("đã được cập nhật");verifyNoInteractions(lifecycle);}
    @Test @com.dienmay.support.TestCase("TC-WEB-34")
    void confirm_afterExpiry_rejected(){order.setExpiresAt(LocalDateTime.now(clock));assertThatThrownBy(()->service.change(5L,1L,OrderStatus.CONFIRMED,null,null,actor)).hasMessageContaining("hết hạn");}
    @Test @com.dienmay.support.TestCase("TC-WEB-34")
    void confirm_jobAlreadyExpired_rejected(){order.setStatus(OrderStatus.EXPIRED);assertThatThrownBy(()->service.change(5L,0L,OrderStatus.CONFIRMED,null,null,actor)).hasMessageContaining("hết hạn");}
    @Test void cancel_missingReason_rejected(){assertThatThrownBy(()->service.change(5L,1L,OrderStatus.CANCELLED," ",null,actor)).hasMessageContaining("bắt buộc");}
    @Test @com.dienmay.support.TestCase("TC-WEB-27")
    void change_wrongBranch_returns404(){doThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng.")).when(access).branch(employee,1L);assertThatThrownBy(()->service.change(5L,1L,OrderStatus.CONFIRMED,null,null,actor)).isInstanceOf(ResourceNotFoundException.class);}
    @Test void confirm_valid_clearsDeadline(){service.change(5L,1L,OrderStatus.CONFIRMED,null,null,actor);assertThat(order.getExpiresAt()).isNull();assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);}
    @Test @com.dienmay.support.TestCase("TC-WEB-23")
    void pickup_shipping_rejected(){order.setStatus(OrderStatus.CONFIRMED);assertThatThrownBy(()->service.change(5L,1L,OrderStatus.SHIPPING,null,null,actor)).isInstanceOf(BusinessException.class);}
}
