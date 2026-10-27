package com.dienmay.orders;
import com.dienmay.orders.web.StaffOrderController;
import com.dienmay.orders.service.*;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.organization.entity.*;
import com.dienmay.config.SecurityConfig;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.entity.Customer;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@WebMvcTest(StaffOrderController.class) @Import(SecurityConfig.class)
class StaffOrderWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;
    @MockitoBean StaffOrderService service;@MockitoBean StaffOrderQueryService queries;
    MockHttpSession staff(String code){Employee e=new Employee();ReflectionTestUtils.setField(e,"id",1L);e.setUsername("nv");e.setPasswordHash("hash");e.setFullName("Nhân viên");Branch b=new Branch();ReflectionTestUtils.setField(b,"id",1L);e.setBranch(b);Role r=new Role();ReflectionTestUtils.setField(r,"code",code);e.setRole(r);var principal=new AppUserDetails(e);var session=new MockHttpSession();session.setAttribute(SecurityConfig.EMPLOYEE_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));return session;}
    @BeforeEach void setup(){when(queries.search(any(),any(),any(),any(),any(),any(),anyInt(),any())).thenReturn(Page.empty());}
    @Test void list_warehouse_forbidden()throws Exception{mvc.perform(get("/staff/orders").session(staff("WAREHOUSE"))).andExpect(status().isForbidden());}
    @Test void list_cashier_allowed()throws Exception{mvc.perform(get("/staff/orders").session(staff("CASHIER")).param("from","2026-10-01").param("to","2026-10-15")).andExpect(status().isOk());verify(queries).search(isNull(),isNull(),eq(LocalDate.of(2026,10,1)),eq(LocalDate.of(2026,10,15)),isNull(),isNull(),eq(0),any());}
    @Test @com.dienmay.support.TestCase("TC-WEB-27")
    void detail_otherBranch_returns404()throws Exception{when(queries.detail(eq(9L),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng."));mvc.perform(get("/staff/orders/9").session(staff("CASHIER"))).andExpect(status().isNotFound());}
    @Test void list_customerOnly_employeeLogin()throws Exception{Customer c=new Customer();c.setEmail("khach@example.test");c.setPasswordHash("hash");c.setFullName("Khách");var principal=new CustomerUserDetails(c);var session=new MockHttpSession();session.setAttribute(SecurityConfig.CUSTOMER_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));mvc.perform(get("/staff/orders").session(session)).andExpect(redirectedUrlPattern("**/login"));}
    @Test void transition_missingCsrf_forbidden()throws Exception{mvc.perform(post("/staff/orders/1/transition").session(staff("CASHIER")).param("version","1").param("target","CONFIRMED")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void transition_staleVersion_flashErrorAndRedirect()throws Exception{when(service.change(eq(1L),eq(0L),eq(OrderStatus.CONFIRMED),any(),any(),any())).thenThrow(new com.dienmay.common.exception.BusinessException("Đơn đã được cập nhật, tải lại."));mvc.perform(post("/staff/orders/1/transition").session(staff("CASHIER")).with(csrf()).param("version","0").param("target","CONFIRMED")).andExpect(redirectedUrl("/staff/orders/1")).andExpect(flash().attribute("error","Đơn đã được cập nhật, tải lại."));}
    @Test void detail_readyForPickup_hasCompleteAndCancelButNotShip()throws Exception{var order=new OrderView(1L,"DH-TEST",OrderStatus.READY_FOR_PICKUP,"HQ",DeliveryType.PICKUP,OrderPaymentMethod.PAY_AT_STORE,"Khách","0901234567",null,null,new BigDecimal("100"),new BigDecimal("100"),null,LocalDateTime.now(),null,null,BigDecimal.ZERO,List.of(),List.of());when(queries.detail(eq(1L),any())).thenReturn(new StaffOrderDetail(order,2L,null,List.of(new StaffOrderDetail.SerialLine(2L,"Máy","SERIAL-QA",com.dienmay.inventory.serial.SerialStatus.RESERVED,false))));mvc.perform(get("/staff/orders/1").session(staff("CASHIER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("SERIAL-QA"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Đã giữ"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Hoàn tất và tạo hóa đơn"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Hủy đơn và nhả hàng"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Bắt đầu giao"))));}
}
