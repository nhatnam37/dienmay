package com.dienmay.sales.web;

import com.dienmay.catalog.entity.*;
import com.dienmay.common.exception.*;
import com.dienmay.config.SecurityConfig;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.entity.Branch;
import com.dienmay.sales.config.PosProperties;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.repository.SaleSerialQueries;
import com.dienmay.sales.security.SalesAccess;
import com.dienmay.sales.service.*;
import com.dienmay.support.InventoryTestUsers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PosController.class)
@Import({SecurityConfig.class,PosProperties.class,SerialLookupForSale.class,SerialNumberRules.class})
class PosWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;
    @MockitoBean CheckoutService checkout;
    @MockitoBean ReturnQueryService returns;
    @MockitoBean PosQueryService queries;
    @MockitoBean SaleSerialQueries serials;
    @MockitoBean SalesAccess access;
    @Test @com.dienmay.support.TestCase("TC-POS-17")
    void counter_warehouse_returns403() throws Exception {
        mvc.perform(get("/pos").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(queries);
    }
    @Test void counter_cashier_rendersCounterAndExternalAssets() throws Exception {
        when(queries.branchName(any())).thenReturn("Chi nhánh A");
        mvc.perform(get("/pos").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Quầy bán hàng"))).andExpect(content().string(containsString("/js/pos.js"))).andExpect(content().string(containsString("/css/app.css"))).andExpect(content().string(containsString("name=\"_csrf\"")));
    }
    @Test void checkout_missingCsrf_returns403() throws Exception {
        mvc.perform(post("/pos/checkout").param("payload","{}").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(checkout);
    }
    @Test void checkout_tamperedBranch_usesPrincipalAndRedirects() throws Exception {
        when(checkout.checkout(any(),any())).thenReturn(invoice());
        mvc.perform(post("/pos/checkout").with(user(InventoryTestUsers.user("CASHIER",1,1))).with(csrf()).param("payload","{\"checkoutToken\":\"abc\",\"branchId\":99}"))
            .andExpect(redirectedUrl("/pos/invoices/7")).andExpect(flash().attribute("completedToken","token"));
        verify(checkout).checkout(argThat(r->r.branchId()==1),argThat(a->a.employeeId()==1&&a.branchId()==1));
    }
    @Test void checkout_businessFailure_redirectsWithReadableMessage() throws Exception {
        when(checkout.checkout(any(),any())).thenThrow(new BusinessException("Serial ABC-123 vừa được bán."));
        mvc.perform(post("/pos/checkout").with(user(InventoryTestUsers.user("CASHIER",1,1))).with(csrf()).param("payload","{}"))
            .andExpect(redirectedUrl("/pos")).andExpect(flash().attribute("error","Serial ABC-123 vừa được bán."));
    }
    @Test void checkout_malformedPayload_redirectsWithoutStackTrace() throws Exception {
        mvc.perform(post("/pos/checkout").with(user(InventoryTestUsers.user("CASHIER",1,1))).with(csrf()).param("payload","not-json"))
            .andExpect(redirectedUrl("/pos")).andExpect(flash().attribute("error",containsString("không hợp lệ")));verifyNoInteractions(checkout);
    }
    @Test @com.dienmay.support.TestCase("TC-POS-18")
    void invoice_otherBranch_returns404() throws Exception {
        when(queries.detail(eq(7L),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy hóa đơn."));
        mvc.perform(get("/pos/invoices/7").with(user(InventoryTestUsers.user("BRANCH_MANAGER",2,1)))).andExpect(status().isNotFound());
    }
    @Test void invoice_otherCashier_returns404() throws Exception {
        when(queries.detail(eq(7L),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy hóa đơn."));
        mvc.perform(get("/pos/invoices/7").with(user(InventoryTestUsers.user("CASHIER",1,2)))).andExpect(status().isNotFound());
    }
    @Test void invoice_allowed_rendersWarrantyPaymentsAndPrintAction() throws Exception {
        when(queries.detail(eq(7L),any())).thenReturn(new InvoiceDetail(invoice(),Map.of(8L,java.time.LocalDate.of(2027,10,14))));
        mvc.perform(get("/pos/invoices/7").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("In hóa đơn"))).andExpect(content().string(containsString("14/10/2027"))).andExpect(content().string(containsString("Hoàn tất"))).andExpect(content().string(containsString("Tiền thối"))).andExpect(content().string(containsString("100 đ"))).andExpect(content().string(containsString("100 đ")));
    }
    @Test void invoices_admin_rendersFiltersAndRows() throws Exception {
        when(queries.search(any(),any(),any(),any(),any(),any(),anyInt(),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(invoice()),org.springframework.data.domain.PageRequest.of(0,10),21));
        when(queries.branchChoices(any())).thenReturn(List.of(new com.dienmay.inventory.dto.InventoryChoice(1L,"A")));
        mvc.perform(get("/pos/invoices").param("phone","090").param("status","COMPLETED").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("HD-20261014-00007"))).andExpect(content().string(containsString("phone=090"))).andExpect(content().string(containsString("status=COMPLETED")));
    }
    @Test void serial_unknown_returnsJsonReason() throws Exception {
        when(serials.findForSaleBySerialNo("ABC-123")).thenReturn(Optional.empty());
        mvc.perform(get("/pos/serials").param("serialNo"," abc-123 ").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(jsonPath("$.sellable").value(false)).andExpect(jsonPath("$.reason").value(containsString("Không tìm thấy")));
    }
    @Test void serial_invalidWhitespace_returnsReason() throws Exception {
        mvc.perform(get("/pos/serials").param("serialNo","AB C").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(jsonPath("$.reason").value(containsString("khoảng trắng")));verifyNoInteractions(serials);
    }
    @ParameterizedTest @EnumSource(SerialStatus.class)
    @com.dienmay.support.TestCase("TC-POS-05")
    void serial_eachStatus_returnsCorrectSellability(SerialStatus state) throws Exception {
        serial(state,1,ProductStatus.ACTIVE);
        var result=mvc.perform(get("/pos/serials").param("serialNo","ABC-123").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state.name())).andExpect(jsonPath("$.sellable").value(state==SerialStatus.IN_STOCK));
        if(state==SerialStatus.RESERVED)result.andExpect(jsonPath("$.reason").value(containsString("đơn online")));
        else if(state!=SerialStatus.IN_STOCK)result.andExpect(jsonPath("$.reason").value(containsString(state.name())));
        else result.andExpect(jsonPath("$.price").value(100));
    }
    @Test @com.dienmay.support.TestCase("TC-POS-05")
    void serial_otherBranch_returnsReason() throws Exception {
        serial(SerialStatus.IN_STOCK,2,ProductStatus.ACTIVE);
        mvc.perform(get("/pos/serials").param("serialNo","ABC-123").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(jsonPath("$.sellable").value(false)).andExpect(jsonPath("$.reason").value(containsString("chi nhánh khác")));
    }
    @Test @com.dienmay.support.TestCase("TC-POS-06")
    void serial_discontinued_returnsReason() throws Exception {
        serial(SerialStatus.IN_STOCK,1,ProductStatus.DISCONTINUED);
        mvc.perform(get("/pos/serials").param("serialNo","ABC-123").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(jsonPath("$.reason").value(containsString("ngừng kinh doanh")));
    }
    @Test void accessories_cashier_returnsJson() throws Exception {
        when(queries.accessories(eq("CAP"),any())).thenReturn(List.of(new AccessoryView(5L,"CAP-01","Cáp",new BigDecimal("100"),3)));
        mvc.perform(get("/pos/accessories").param("keyword","CAP").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(jsonPath("$[0].availableQuantity").value(3));
    }
    void serial(SerialStatus state,long branchId,ProductStatus productStatus){
        Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",branchId);ReflectionTestUtils.setField(branch,"name","A");Product product=new Product();ReflectionTestUtils.setField(product,"id",3L);product.setSku("SKU");product.setName("Máy");product.setPrice(new BigDecimal("100"));product.setStatus(productStatus);
        SerialItem serial=new SerialItem();serial.setProduct(product);serial.setBranch(branch);ReflectionTestUtils.setField(serial,"serialNo","ABC-123");ReflectionTestUtils.setField(serial,"status",state);when(serials.findForSaleBySerialNo("ABC-123")).thenReturn(Optional.of(serial));
    }
    InvoiceView invoice(){return new InvoiceView(7L,"HD-20261014-00007","token",1L,"A",1L,"Thu ngân","Khách","0901234567",new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("100"),InvoiceStatus.COMPLETED,null,LocalDateTime.of(2026,10,14,10,30),List.of(new InvoiceItemView(8L,3L,"SKU","Máy","ABC-123",1,new BigDecimal("100"),new BigDecimal("100"))),List.of(new PaymentView(PaymentMethod.CASH,new BigDecimal("100"),new BigDecimal("200"),new BigDecimal("100"),null,LocalDateTime.now())));}

    @Test void invoice_managerEligible_showsReturnAction() throws Exception {
        when(queries.detail(eq(7L),any())).thenReturn(new InvoiceDetail(invoice(),Map.of()));
        when(returns.screen(eq(7L),any())).thenReturn(new ReturnScreen(invoice(),List.of(),true,true,null));
        mvc.perform(get("/pos/invoices/7").with(user(InventoryTestUsers.user("BRANCH_MANAGER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Đổi trả / Hủy")));
    }
    @Test void invoice_cashier_neverShowsReturnAction() throws Exception {
        when(queries.detail(eq(7L),any())).thenReturn(new InvoiceDetail(invoice(),Map.of()));
        mvc.perform(get("/pos/invoices/7").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(content().string(not(containsString("Đổi trả / Hủy"))));verify(returns,never()).screen(anyLong(),any());
    }

    @Test void invoice_returnedSerial_displaysVoidedWarranty() throws Exception {
        when(queries.detail(eq(7L),any())).thenReturn(new InvoiceDetail(invoice(),Map.of(8L,java.time.LocalDate.of(2027,10,15)),Set.of(8L)));
        mvc.perform(get("/pos/invoices/7").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Đã hủy bảo hành")));
    }
}
