package com.dienmay.reports;
import com.dienmay.reports.web.*;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.organization.entity.*;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.entity.Customer;
import com.dienmay.config.SecurityConfig;
import com.dienmay.common.exception.*;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({ReportController.class,ReportApiController.class,ReportExportController.class})
@Import({SecurityConfig.class,ReportWebTest.Time.class})
class ReportWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) static class Time {
        @org.springframework.context.annotation.Bean Clock clock(){return Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"),ZoneOffset.UTC);}
    }
    @Autowired MockMvc mvc;@MockitoBean ReportQueryService reports;@MockitoBean ReportScreenService screens;
    @MockitoBean ReportExcelService exports;@MockitoBean ReportProperties properties;
    MockHttpSession staff(String code){Employee e=new Employee();ReflectionTestUtils.setField(e,"id",1L);e.setUsername("nv");e.setPasswordHash("hash");e.setFullName("Nhân viên");Branch b=new Branch();ReflectionTestUtils.setField(b,"id",1L);e.setBranch(b);Role r=new Role();ReflectionTestUtils.setField(r,"code",code);e.setRole(r);var principal=new AppUserDetails(e);var session=new MockHttpSession();session.setAttribute(SecurityConfig.EMPLOYEE_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));return session;}
    MockHttpSession customer(long id){Customer c=new Customer();ReflectionTestUtils.setField(c,"id",id);c.setEmail("khach@example.test");c.setPasswordHash("hash");c.setFullName("Khách");var principal=new CustomerUserDetails(c);var session=new MockHttpSession();session.setAttribute(SecurityConfig.CUSTOMER_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));return session;}

    ReportRange range=new ReportRange(LocalDate.of(2026,9,22),LocalDate.of(2026,10,21));
    @BeforeEach void fixture(){
        when(screens.meta(any(),anyBoolean(),any())).thenAnswer(i->{com.dienmay.sales.security.SalesActor a=i.getArgument(2);return new ReportScreenService.Meta(new ReportScope(a.hasRole("ADMIN")?null:1L),"HQ","Nhân viên",LocalDateTime.of(2026,10,21,8,0),a.hasRole("ADMIN"),List.of(new ReportScreenService.Choice(1L,"HQ")),List.of());});
        var d=new ReportDtos.Delta(java.math.BigDecimal.ZERO,null);var compare=new Comparison(range,range.previous(),Totals.zero(),Totals.zero(),d,d,d,d,d,d);
        when(reports.dashboard(any(),any())).thenReturn(new Dashboard(compare,Totals.zero(),0,0,0));when(reports.summary(any(),any(),any())).thenReturn(compare);
        when(reports.revenueSeries(any(),any(),any(),any())).thenReturn(List.of());when(reports.topProducts(any(),any(),anyInt(),any(),any())).thenReturn(List.of());
        when(reports.inventoryStock(any(),any(),any(),any(),any())).thenReturn(Page.empty());when(reports.lowStock(any(),any(),any())).thenReturn(List.of());
    }
    @Test @com.dienmay.support.TestCase("TC-RPT-14")
    void pages_cashierAndWarrantyStaff_allForbidden()throws Exception{for(String role:List.of("CASHIER","WARRANTY_STAFF"))for(String path:List.of("dashboard","revenue","products","inventory","low-stock"))mvc.perform(get("/reports/"+path).session(staff(role))).andExpect(status().isForbidden());}
    @Test @com.dienmay.support.TestCase("TC-RPT-14")
    @com.dienmay.support.TestCase("TC-RPT-15")
    @com.dienmay.support.TestCase("TC-RPT-16")
    void reports_warehouse_allowsInventoryDeniesRevenueAndExports()throws Exception{mvc.perform(get("/reports/inventory").session(staff("WAREHOUSE"))).andExpect(status().isOk());for(String path:List.of("revenue","dashboard","products","api/revenue-series","export/revenue","export/products"))mvc.perform(get("/reports/"+path).session(staff("WAREHOUSE"))).andExpect(status().isForbidden());}
    @Test void manager_pagesAllowed_adminOnlyControlsAbsent()throws Exception{for(String path:List.of("dashboard","revenue","products","inventory","low-stock"))mvc.perform(get("/reports/"+path).session(staff("BRANCH_MANAGER"))).andExpect(status().isOk()).andExpect(content().string(not(containsString("id=\"branchId\""))));mvc.perform(get("/reports/dashboard").session(staff("BRANCH_MANAGER"))).andExpect(content().string(not(containsString("data-report-chart=\"by-branch\""))));}
    @Test @com.dienmay.support.TestCase("TC-RPT-11")
    void dashboard_adminBranchSelectorAndChart_present()throws Exception{mvc.perform(get("/reports/dashboard").session(staff("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("id=\"branchId\""))).andExpect(content().string(containsString("data-report-chart=\"by-branch\""))).andExpect(content().string(containsString("Không so sánh được")));}
    @Test void reports_customerContext_requiresEmployeeLogin()throws Exception{mvc.perform(get("/reports/dashboard").session(customer(5L))).andExpect(redirectedUrlPattern("**/login"));}
    @Test @com.dienmay.support.TestCase("TC-RPT-16")
    void reports_cashier_deniesJsonAndExcel()throws Exception{for(String path:List.of("api/revenue-series","api/by-channel","api/top-products","api/by-branch","export/revenue","export/products","export/inventory"))mvc.perform(get("/reports/"+path).session(staff("CASHIER"))).andExpect(status().isForbidden());}
    @Test @com.dienmay.support.TestCase("TC-RPT-13")
    void json_managerOtherBranch_returns404()throws Exception{when(reports.revenueSeries(any(),any(),eq(new ReportScope(9L)),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy báo cáo tại chi nhánh này."));mvc.perform(get("/reports/api/revenue-series").session(staff("BRANCH_MANAGER")).param("branchId","9")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Không tìm thấy báo cáo tại chi nhánh này."));}
    @Test @com.dienmay.support.TestCase("TC-RPT-16")
    void json_byBranchManager_forbidden()throws Exception{mvc.perform(get("/reports/api/by-branch").session(staff("BRANCH_MANAGER"))).andExpect(status().isForbidden());}
    @Test @com.dienmay.support.TestCase("TC-RPT-12")
    void period_invalidOrLong_friendlyServerError()throws Exception{for(String[] dates:List.of(new String[]{"2026-10-22","2026-10-21"},new String[]{"2025-01-01","2026-10-21"}))mvc.perform(get("/reports/revenue").session(staff("ADMIN")).param("from",dates[0]).param("to",dates[1])).andExpect(status().isBadRequest()).andExpect(content().string(containsString("alert-danger")));}
    @Test void inventory_serialBadgesAndPaging_render()throws Exception{var item=new InventoryRow(1L,"HQ",1L,"SKU","Máy", "Tivi",true,new SerialCounts(2,1,3,4,5,6),null,null,2);when(reports.inventoryStock(any(),any(),any(),any(),any())).thenReturn(new PageImpl<>(List.of(item),PageRequest.of(0,20),21));mvc.perform(get("/reports/inventory").session(staff("WAREHOUSE")).param("keyword","Tivi")).andExpect(status().isOk()).andExpect(content().string(containsString("Đang bảo hành"))).andExpect(content().string(containsString("keyword=Tivi")));}
    @Test @com.dienmay.support.TestCase("TC-RPT-04")
    void revenue_negativeWarningAndMoneyFormatting_render()throws Exception{when(reports.revenueSeries(any(),any(),any(),any())).thenReturn(List.of(new RevenuePoint(LocalDate.of(2026,10,21),new Totals(java.math.BigDecimal.ZERO,new java.math.BigDecimal("1000000"),new java.math.BigDecimal("-1000000"),0,0,-1))));mvc.perform(get("/reports/revenue").session(staff("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("-1.000.000 đ"))).andExpect(content().string(containsString("report-negative")));}
    @Test @com.dienmay.support.TestCase("TC-RPT-15")
    void export_warehouseInventory_returnsHeadersAndRemovesTempFile()throws Exception {
        var path=java.nio.file.Files.createTempFile("export-test-",".xlsx");java.nio.file.Files.write(path,new byte[]{1,2,3});
        when(exports.export(eq("inventory"),any(),any(),any(),any(),any(),any(),any())).thenReturn(new ReportExcelService.Export(path,"bao-cao-ton-kho-20261001-20261031.xlsx"));
        mvc.perform(get("/reports/export/inventory").session(staff("WAREHOUSE")))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition",containsString("bao-cao-ton-kho-20261001-20261031.xlsx")));
        assertThat(java.nio.file.Files.exists(path)).isFalse();
    }
    @Test @com.dienmay.support.TestCase("TC-RPT-19")
    void export_tooManyRows_returnsFriendlyJsonWithoutXlsx()throws Exception {
        when(exports.export(any(),any(),any(),any(),any(),any(),any(),any())).thenThrow(new BusinessException("Vui lòng thu hẹp kỳ báo cáo"));
        mvc.perform(get("/reports/export/revenue").session(staff("ADMIN"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vui lòng thu hẹp kỳ báo cáo"));
    }
    @Test void json_invalidFilter_friendly400()throws Exception{mvc.perform(get("/reports/api/revenue-series").session(staff("ADMIN")).param("granularity","BAD")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());}

}
