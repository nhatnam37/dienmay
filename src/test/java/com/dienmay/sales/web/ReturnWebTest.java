package com.dienmay.sales.web;
import com.dienmay.config.SecurityConfig;
import com.dienmay.sales.service.*;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.common.exception.*;
import com.dienmay.support.InventoryTestUsers;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ReturnController.class) @Import(SecurityConfig.class)
class ReturnWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;@MockitoBean ReturnService service;@MockitoBean ReturnQueryService queries;@MockitoBean PosQueryService pos;
    @ParameterizedTest @ValueSource(strings={"CASHIER","WAREHOUSE"})
    void returns_unauthorizedRole_returns403(String role) throws Exception {
        mvc.perform(get("/pos/returns").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isForbidden());
        mvc.perform(get("/pos/invoices/7/returns/new").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isForbidden());
        mvc.perform(post("/pos/invoices/7/cancel").param("reason","Test").with(user(InventoryTestUsers.user(role,1,1))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(service,queries);
    }
    @ParameterizedTest @ValueSource(strings={"returns","cancel"})
    void submit_missingCsrf_returns403(String action) throws Exception {
        mvc.perform(post("/pos/invoices/7/"+action).with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void form_managerEligible_rendersBothActionsAndExternalJavascript() throws Exception {
        when(queries.screen(eq(7L),any())).thenReturn(screen(true,true));
        mvc.perform(get("/pos/invoices/7/returns/new").with(user(InventoryTestUsers.user("BRANCH_MANAGER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Xác nhận trả hàng"))).andExpect(content().string(containsString("Hủy hóa đơn"))).andExpect(content().string(containsString("/js/pos.js")));
    }
    @Test void form_noLongerEligible_hidesAllWriteActions() throws Exception {
        when(queries.screen(eq(7L),any())).thenReturn(screen(false,false));
        mvc.perform(get("/pos/invoices/7/returns/new").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk()).andExpect(content().string(not(containsString("Xác nhận trả hàng")))).andExpect(content().string(not(containsString("data-confirm="))));
    }
    @Test void form_otherBranch_returns404() throws Exception {
        when(queries.screen(eq(7L),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy hóa đơn."));mvc.perform(get("/pos/invoices/7/returns/new").with(user(InventoryTestUsers.user("BRANCH_MANAGER",2,1)))).andExpect(status().isNotFound());
    }
    @Test void submit_valid_redirectsToReturnDocument() throws Exception {
        when(service.returnItems(eq(7L),any(),any())).thenReturn(42L);
        mvc.perform(post("/pos/invoices/7/returns").with(user(InventoryTestUsers.user("BRANCH_MANAGER",1,1))).with(csrf()).param("reason","Khách trả").param("lines[0].invoiceItemId","8").param("lines[0].quantity","1")).andExpect(redirectedUrl("/pos/returns/42"));
    }
    @Test void submit_businessFailure_preservesReasonAndQuantitiesByRedirect() throws Exception {
        when(service.returnItems(eq(7L),any(),any())).thenThrow(new BusinessException("Serial đang bảo hành"));
        mvc.perform(post("/pos/invoices/7/returns").with(user(InventoryTestUsers.user("ADMIN",1,1))).with(csrf()).param("reason","Giữ lý do").param("lines[0].invoiceItemId","8").param("lines[0].quantity","1"))
            .andExpect(redirectedUrl("/pos/invoices/7/returns/new")).andExpect(flash().attribute("error","Serial đang bảo hành")).andExpect(flash().attributeExists("form"));
    }
    @Test void cancel_admin_successRedirects() throws Exception {
        when(service.cancelInvoice(eq(7L),eq("Khách hủy"),any())).thenReturn(42L);
        mvc.perform(post("/pos/invoices/7/cancel").with(user(InventoryTestUsers.user("ADMIN",1,1))).with(csrf()).param("reason","Khách hủy")).andExpect(redirectedUrl("/pos/returns/42"));
    }
    @Test void list_manager_rendersBranchReturns() throws Exception {
        when(queries.list(any(),anyInt(),any())).thenReturn(Page.empty());
        mvc.perform(get("/pos/returns").with(user(InventoryTestUsers.user("BRANCH_MANAGER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Phiếu trả hàng")));
    }
    @Test void detail_admin_rendersOriginalMethodAndWholeRefund() throws Exception {
        when(queries.detail(eq(42L),any())).thenReturn(new ReturnDetail(new ReturnSummary(42L,"TH-42",7L,"HD-7",1L,"A",ReturnType.PARTIAL_RETURN,"Khách trả",new BigDecimal("100"),LocalDateTime.now()),"Quản lý",List.of(new ReturnDetail.Item("Máy","ABC-123",1,new BigDecimal("100"))),List.of(new ReturnDetail.Refund(PaymentMethod.CARD,"CARD123",new BigDecimal("100")))));
        mvc.perform(get("/pos/returns/42").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Thẻ"))).andExpect(content().string(containsString("100 đ"))).andExpect(content().string(containsString("CARD123")));
    }
    ReturnScreen screen(boolean cancel,boolean partial){return new ReturnScreen(new InvoiceView(7L,"HD-7","token",1L,"A",1L,"Thu ngân",null,null,new BigDecimal("100"),BigDecimal.ZERO,new BigDecimal("100"),InvoiceStatus.COMPLETED,null,LocalDateTime.now(),List.of(),List.of()),List.of(new ReturnLineView(8L,"Máy","ABC-123",1,0,1,new BigDecimal("100"),true,null)),cancel,partial,null);}
}
