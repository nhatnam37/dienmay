package com.dienmay.orders;
import com.dienmay.orders.web.OrderController;
import com.dienmay.orders.service.*;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.entity.*;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.entity.Customer;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.storefront.cart.CartService;
import com.dienmay.storefront.dto.CartView;
import com.dienmay.config.SecurityConfig;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class,OrderWebTest.Time.class})
class OrderWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @org.springframework.boot.test.context.TestConfiguration static class Time { @Bean Clock clock(){return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));} }
    @Autowired MockMvc mvc;
    @MockitoBean OrderPlacementService placement;@MockitoBean OrderLifecycleService lifecycle;@MockitoBean OrderQueryService queries;@MockitoBean CheckoutPageService pages;@MockitoBean CustomerService customers;@MockitoBean CartService carts;
    MockHttpSession session;
    @BeforeEach void setup(){Customer c=new Customer();ReflectionTestUtils.setField(c,"id",1L);c.setEmail("a@example.test");c.setPasswordHash("hash");c.setFullName("Khách A");var principal=new CustomerUserDetails(c);session=new MockHttpSession();session.setAttribute(SecurityConfig.CUSTOMER_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));when(carts.view(any())).thenReturn(new CartView(List.of(),BigDecimal.ZERO,false));}
    @Test @com.dienmay.support.TestCase("TC-WEB-22")
    void detail_otherCustomer_returns404()throws Exception{when(queries.get(eq("DH-B"),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng."));mvc.perform(get("/orders/DH-B").session(session)).andExpect(status().isNotFound());}
    @Test @com.dienmay.support.TestCase("TC-WEB-22")
    void payment_otherCustomer_returns404()throws Exception{when(queries.get(eq("DH-B"),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng."));mvc.perform(get("/checkout/pay/DH-B").session(session)).andExpect(status().isNotFound());}
    @Test @com.dienmay.support.TestCase("TC-WEB-05")
    void checkout_anonymous_customerLogin()throws Exception{mvc.perform(get("/checkout")).andExpect(redirectedUrlPattern("**/customer/login"));}
    @Test void checkout_employeeOnly_cannotUseCustomerPage()throws Exception{var staff=new MockHttpSession();staff.setAttribute(SecurityConfig.EMPLOYEE_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("staff",null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))));mvc.perform(get("/checkout").session(staff)).andExpect(redirectedUrlPattern("**/customer/login"));}
    @Test void place_missingCsrf_forbidden()throws Exception{mvc.perform(post("/checkout").session(session)).andExpect(status().isForbidden());verifyNoInteractions(placement);}
    @Test void pay_missingCsrf_forbidden()throws Exception{mvc.perform(post("/checkout/pay/DH-A").session(session)).andExpect(status().isForbidden());verifyNoInteractions(lifecycle);}
    @Test void cancel_missingCsrf_forbidden()throws Exception{mvc.perform(post("/orders/DH-A/cancel").session(session)).andExpect(status().isForbidden());verifyNoInteractions(lifecycle);}
    @Test void pay_ownerWithCsrf_usesAuthenticatedCustomerAndRedirects()throws Exception{mvc.perform(post("/checkout/pay/DH-A").session(session).with(csrf())).andExpect(redirectedUrl("/orders/DH-A"));verify(lifecycle).pay("DH-A",new com.dienmay.orders.security.CustomerActor(1L));}
    @Test void cancel_ownerWithCsrf_redirects()throws Exception{mvc.perform(post("/orders/DH-A/cancel").session(session).with(csrf()).param("reason","Đổi ý")).andExpect(redirectedUrl("/orders/DH-A"));verify(lifecycle).cancel("DH-A","Đổi ý",new com.dienmay.orders.security.CustomerActor(1L));}
    @Test void place_failure_keepsCartAndForm()throws Exception{
        when(customers.get(1L)).thenReturn(new com.dienmay.customer.dto.CustomerView(1L,"a@example.test","0901234567","Khách A",null));
        var opened=mvc.perform(get("/checkout").session(session)).andExpect(status().isOk()).andReturn();String token=((CheckoutForm)opened.getModelAndView().getModel().get("checkoutForm")).getPlaceToken();
        when(placement.place(any(),any())).thenThrow(new com.dienmay.common.exception.BusinessException("Hàng vừa hết"));
        mvc.perform(post("/checkout").session(session).with(csrf()).param("placeToken",token).param("branchId","1").param("deliveryType","PICKUP").param("paymentMethod","ONLINE").param("receiverName","Khách A").param("receiverPhone","0901234567")).andExpect(status().isOk()).andExpect(model().attributeHasErrors("checkoutForm")).andExpect(content().string(org.hamcrest.Matchers.containsString("Hàng vừa hết")));
        verify(carts,never()).clear(any());
    }

    @Test @com.dienmay.support.TestCase("TC-WEB-22")
    void customerOrders_statusFilter_usesOwnIdAndRendersPaging()throws Exception{
        when(queries.customerPage(eq(new com.dienmay.orders.security.CustomerActor(1L)),eq(OrderStatus.PLACED),eq(1))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(),org.springframework.data.domain.PageRequest.of(1,10),11));
        mvc.perform(get("/account/orders").session(session).param("status","PLACED").param("page","1")).andExpect(status().isOk()).andExpect(view().name("storefront/orders/list"));verify(queries).customerPage(new com.dienmay.orders.security.CustomerActor(1L),OrderStatus.PLACED,1);
    }

}
