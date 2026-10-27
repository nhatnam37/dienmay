package com.dienmay.storefront;

import com.dienmay.config.SecurityConfig;
import com.dienmay.storefront.web.*;
import com.dienmay.storefront.service.StorefrontService;
import com.dienmay.storefront.cart.CartService;
import com.dienmay.storefront.dto.*;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest({StorefrontController.class,CartController.class})
@Import(SecurityConfig.class)
class StorefrontWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;
    @MockitoBean StorefrontService products;
    @MockitoBean CartService carts;
    @BeforeEach void setup(){when(products.search(any(),any(),any(),any(),any(),anyString(),anyInt())).thenReturn(Page.empty());when(carts.view(any())).thenReturn(new CartView(List.of(),BigDecimal.ZERO,false));}
    @Test void home_anonymous_public()throws Exception{mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("storefront/home"));}
    @Test @com.dienmay.support.TestCase("TC-WEB-04")
    void products_anonymous_public()throws Exception{mvc.perform(get("/products")).andExpect(status().isOk());}
    @Test void search_anonymous_public()throws Exception{mvc.perform(get("/search").param("keyword","Tivi")).andExpect(status().isOk());}
    @Test @com.dienmay.support.TestCase("TC-WEB-36")
    void cart_anonymous_public()throws Exception{mvc.perform(get("/cart")).andExpect(status().isOk());}
    @Test @com.dienmay.support.TestCase("TC-WEB-05")
    void checkout_anonymous_customerLogin()throws Exception{mvc.perform(get("/checkout")).andExpect(redirectedUrlPattern("**/customer/login"));}
    @Test void cartPost_withoutCsrf_forbidden()throws Exception{mvc.perform(post("/cart/clear")).andExpect(status().isForbidden());verify(carts,never()).clear(any());}
    @Test void cartPost_anonymousWithCsrf_redirects()throws Exception{mvc.perform(post("/cart/items").with(csrf()).param("productId","1").param("quantity","2")).andExpect(redirectedUrl("/cart"));verify(carts).add(any(),eq(1L),eq(2));}
    @Test @com.dienmay.support.TestCase("TC-WEB-08")
    void detail_discontinued_returns404()throws Exception{when(products.detail(9L)).thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm."));mvc.perform(get("/products/9")).andExpect(status().isNotFound()).andExpect(view().name("storefront/not-found"));}
    @Test @com.dienmay.support.TestCase("TC-WEB-04")
    void detail_available_rendersOnlyPublicInformation()throws Exception{when(products.detail(1L)).thenReturn(new ProductDetail(new ProductCard(1L,"Tivi",null,new BigDecimal("1000000"),"Tivi","Sony"),"Mô tả",24,List.of(new BranchAvailability(1L,1L,"Trụ sở",7))));mvc.perform(get("/products/1")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("1.000.000 đ"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Còn hàng"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("cost_price"))));}
    @Test @WithMockUser(roles="ADMIN") void checkout_injectedEmployeeAuthentication_forbidden()throws Exception{mvc.perform(get("/checkout")).andExpect(status().isForbidden());}
}
