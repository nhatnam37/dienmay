package com.dienmay.warranty;
import com.dienmay.warranty.web.*;
import com.dienmay.warranty.service.*;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.dto.WarrantyScreens.*;
import com.dienmay.warranty.entity.*;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.organization.entity.*;
import com.dienmay.config.SecurityConfig;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.entity.Customer;
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
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@WebMvcTest({PublicWarrantyController.class,StaffWarrantyController.class,CustomerWarrantyController.class})
@Import({SecurityConfig.class,PublicWarrantyLookupService.class,WarrantyLookupRateLimiter.class,WarrantyWebTest.TestTime.class})
class WarrantyWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class TestTime {@org.springframework.context.annotation.Bean Clock warrantyWebClock(){return Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"),ZoneOffset.UTC);}}
    @Autowired MockMvc mvc;
    @MockitoBean WarrantyQueryService publicQueries;@MockitoBean WarrantyScreenService screens;@MockitoBean WarrantyTicketService service;
    MockHttpSession staff(String code){Employee e=new Employee();ReflectionTestUtils.setField(e,"id",1L);e.setUsername("nv");e.setPasswordHash("hash");e.setFullName("Nhân viên");Branch b=new Branch();ReflectionTestUtils.setField(b,"id",1L);e.setBranch(b);Role r=new Role();ReflectionTestUtils.setField(r,"code",code);e.setRole(r);var principal=new AppUserDetails(e);var session=new MockHttpSession();session.setAttribute(SecurityConfig.EMPLOYEE_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));return session;}
    MockHttpSession customer(long id){Customer c=new Customer();ReflectionTestUtils.setField(c,"id",id);c.setEmail("khach@example.test");c.setPasswordHash("hash");c.setFullName("Khách");var principal=new CustomerUserDetails(c);var session=new MockHttpSession();session.setAttribute(SecurityConfig.CUSTOMER_CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));return session;}
    @BeforeEach void defaults(){when(screens.search(any(),any(),any(),any(),any(),any(),any(),anyInt(),any())).thenReturn(Page.empty());when(screens.branches(any())).thenReturn(List.of());}
    TicketRow row(){return new TicketRow(1L,"BH-20261021-00001","SERIAL-1","Máy giặt","HQ",WarrantyTicketStatus.RECEIVED,LocalDateTime.of(2026,10,21,8,0));}
    Detail detail(WarrantyTicketStatus status){var ticket=new WarrantyTicketView(1L,"BH-20261021-00001",2L,3L,"SERIAL-1",1L,status,"Không lên nguồn",false,null,"CHẨN ĐOÁN NỘI BỘ","KẾT QUẢ NỘI BỘ",null,LocalDateTime.of(2026,10,21,8,0),null,null,2L);return new Detail(ticket,"Máy giặt","HQ","Khách Bí Mật","0909999999",9L,List.of());}
    @Test void publicPages_anonymous_accessibleWithCustomerCsrfForms()throws Exception{
        mvc.perform(get("/warranty")).andExpect(status().isOk()).andExpect(content().string(containsString("Tra cứu bảo hành"))).andExpect(content().string(containsString("method=\"post\""))).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/warranty/track")).andExpect(status().isOk()).andExpect(content().string(containsString("Mã phiếu")));
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-03")
    void lookup_success_noPiiAndNoSerialInResponseUrl()throws Exception{
        when(publicQueries.publicLookup("SERIAL-SECRET")).thenReturn(Optional.of(new PublicWarrantyView("Máy giặt",LocalDate.of(2026,1,1),LocalDate.of(2027,1,1),true,72,WarrantyTicketStatus.IN_REPAIR)));
        mvc.perform(post("/warranty").with(csrf()).param("serialNo","SERIAL-SECRET")).andExpect(status().isOk()).andExpect(header().doesNotExist("Location")).andExpect(content().string(containsString("Còn hạn (còn 72 ngày)"))).andExpect(content().string(containsString("Đang sửa"))).andExpect(content().string(not(containsString("Khách Bí Mật")))).andExpect(content().string(not(containsString("0909999999")))).andExpect(content().string(not(containsString("SERIAL-SECRET"))));
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-04")
    void lookup_unknownAndUnsold_renderExactlySameVisibleContent()throws Exception{
        when(publicQueries.publicLookup("UNKNOWN")).thenReturn(Optional.empty());when(publicQueries.publicLookup("UNSOLD")).thenReturn(Optional.empty());
        String a=mvc.perform(post("/warranty").with(csrf()).param("serialNo","UNKNOWN")).andExpect(status().isOk()).andExpect(content().string(containsString(PublicWarrantyController.NOT_FOUND))).andReturn().getResponse().getContentAsString();
        String b=mvc.perform(post("/warranty").with(csrf()).param("serialNo","UNSOLD")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(a.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ")).isEqualTo(b.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," "));
    }
    @Test void track_success_safeTimelineOnly()throws Exception{
        when(publicQueries.publicTrack("BH-TEST","SERIAL-1")).thenReturn(Optional.of(new PublicTicketView(WarrantyTicketStatus.IN_REPAIR,LocalDateTime.of(2026,10,21,8,0),null,null,List.of(new PublicTicketView.Event(WarrantyTicketStatus.RECEIVED,LocalDateTime.of(2026,10,21,8,0))))));
        mvc.perform(post("/warranty/track").with(csrf()).param("ticketCode","BH-TEST").param("serialNo","SERIAL-1")).andExpect(status().isOk()).andExpect(content().string(containsString("Đã tiếp nhận"))).andExpect(content().string(not(containsString("CHẨN ĐOÁN NỘI BỘ"))));
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-05")
    void lookup_twentyFirstSharedRequest_friendly429()throws Exception{
        for(int n=0;n<20;n++)mvc.perform(post(n%2==0?"/warranty":"/warranty/track").with(request->{request.setRemoteAddr("192.0.2.21");return request;}).with(csrf()).param("serialNo","UNKNOWN").param("ticketCode","BH-UNKNOWN")).andExpect(status().isOk());
        mvc.perform(post("/warranty").with(request->{request.setRemoteAddr("192.0.2.21");return request;}).with(csrf()).param("serialNo","UNKNOWN")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60")).andExpect(content().string(containsString("Vui lòng chờ một phút")));
    }
    @Test void publicPost_missingCsrf_forbidden()throws Exception{mvc.perform(post("/warranty").param("serialNo","UNKNOWN")).andExpect(status().isForbidden());}
    @Test @com.dienmay.support.TestCase("TC-WAR-22")
    void staff_cashierAndWarehouse_forbidden()throws Exception{for(String role:List.of("CASHIER","WAREHOUSE"))mvc.perform(get("/staff/warranty").session(staff(role))).andExpect(status().isForbidden());}
    @Test void staff_warrantyStaff_allowedAndFiltersParsed()throws Exception{
        mvc.perform(get("/staff/warranty").session(staff("WARRANTY_STAFF"))).andExpect(status().isOk());
        mvc.perform(get("/staff/warranty/tickets").session(staff("WARRANTY_STAFF")).param("from","2026-10-01").param("to","2026-10-21")).andExpect(status().isOk());
        verify(screens).search(isNull(),isNull(),isNull(),isNull(),isNull(),eq(LocalDate.of(2026,10,1)),eq(LocalDate.of(2026,10,21)),eq(0),any());
        mvc.perform(get("/staff/warranty/lookup").session(staff("WARRANTY_STAFF"))).andExpect(status().isOk());
    }
    @Test void staff_customerOnly_redirectedToEmployeeLogin()throws Exception{mvc.perform(get("/staff/warranty").session(customer(5L))).andExpect(redirectedUrlPattern("**/login"));verifyNoInteractions(service);}
    @Test void staffPost_missingCsrf_forbidden()throws Exception{mvc.perform(post("/staff/warranty/tickets/1/transition").session(staff("WARRANTY_STAFF")).param("version","1").param("target","IN_REPAIR")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test @com.dienmay.support.TestCase("TC-WAR-21")
    void detail_otherBranch_returns404()throws Exception{when(screens.detail(eq(9L),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy phiếu."));mvc.perform(get("/staff/warranty/tickets/9").session(staff("WARRANTY_STAFF"))).andExpect(status().isNotFound());}
    @Test @com.dienmay.support.TestCase("TC-WAR-23")
    void customerPage_authenticatedCustomer_usesPrincipalIdOnly()throws Exception{
        var warranty=new CustomerWarrantyView(1L,"MY-SERIAL","Máy của tôi","HD-MY",LocalDate.of(2026,1,1),LocalDate.of(2027,1,1),"ACTIVE",true,72);
        when(screens.customerProfiles(5L)).thenReturn(List.of(new CustomerProfile(warranty,List.of())));
        mvc.perform(get("/account/warranties").session(customer(5L)).param("customerId","999")).andExpect(status().isOk()).andExpect(content().string(containsString("MY-SERIAL"))).andExpect(content().string(not(containsString("OTHER-SERIAL"))));verify(screens).customerProfiles(5L);verify(screens,never()).customerProfiles(999L);
    }
    @Test void customerPage_employeeOnly_customerLogin()throws Exception{mvc.perform(get("/account/warranties").session(staff("ADMIN"))).andExpect(redirectedUrlPattern("**/customer/login"));}
    @Test @com.dienmay.support.TestCase("TC-WAR-20")
    void transition_staleVersion_flashAndRedirect()throws Exception{when(service.startRepair(eq(1L),eq(0L),any())).thenThrow(new BusinessException("Phiếu đã được cập nhật, tải lại."));mvc.perform(post("/staff/warranty/tickets/1/transition").session(staff("WARRANTY_STAFF")).with(csrf()).param("version","0").param("target","IN_REPAIR")).andExpect(redirectedUrl("/staff/warranty/tickets/1")).andExpect(flash().attribute("error","Phiếu đã được cập nhật, tải lại."));}
    @Test @com.dienmay.support.TestCase("TC-WAR-08")
    void receive_validation_keepsFormInFlash()throws Exception{mvc.perform(post("/staff/warranty/tickets").session(staff("WARRANTY_STAFF")).with(csrf()).param("serialNo","SERIAL-1").param("issueDescription"," ")).andExpect(redirectedUrl("/staff/warranty/tickets/new")).andExpect(flash().attributeExists("receiveForm"));verifyNoInteractions(service);}
    @Test @com.dienmay.support.TestCase("TC-WAR-09")
    void new_expiredStaff_needsManagerAndNoOverrideInput()throws Exception{when(screens.intake(eq("SERIAL-1"),any())).thenReturn(new Intake(2L,"SERIAL-1","Máy giặt","Khách",LocalDate.of(2020,1,1),false,0,false,false));mvc.perform(get("/staff/warranty/tickets/new").session(staff("WARRANTY_STAFF")).param("serialNo","SERIAL-1")).andExpect(status().isOk()).andExpect(content().string(containsString("nhờ quản lý"))).andExpect(content().string(not(containsString("id=\"overrideReason\""))));}
    @Test void new_expiredManager_hasRequiredOverrideInput()throws Exception{when(screens.intake(eq("SERIAL-1"),any())).thenReturn(new Intake(2L,"SERIAL-1","Máy giặt","Khách",LocalDate.of(2020,1,1),false,0,true,true));mvc.perform(get("/staff/warranty/tickets/new").session(staff("BRANCH_MANAGER")).param("serialNo","SERIAL-1")).andExpect(status().isOk()).andExpect(content().string(containsString("Lý do nhận ngoài bảo hành")));}
    @ParameterizedTest @EnumSource(WarrantyTicketStatus.class) void detail_actions_matchStatus(WarrantyTicketStatus status)throws Exception{
        when(screens.detail(eq(1L),any())).thenReturn(detail(status));String html=mvc.perform(get("/staff/warranty/tickets/1").session(staff("WARRANTY_STAFF"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html.contains("Bắt đầu sửa</button>")).isEqualTo(status==WarrantyTicketStatus.RECEIVED);
        assertThat(html.contains("Hoàn tất sửa chữa</button>")).isEqualTo(status==WarrantyTicketStatus.IN_REPAIR);
        assertThat(html.contains("Trả khách</button>")).isEqualTo(status==WarrantyTicketStatus.COMPLETED||status==WarrantyTicketStatus.REJECTED);
        assertThat(html.contains("Từ chối bảo hành</button>")).isEqualTo(status==WarrantyTicketStatus.RECEIVED||status==WarrantyTicketStatus.IN_REPAIR);
        assertThat(html.contains("Hủy phiếu</button>")).isEqualTo(status==WarrantyTicketStatus.RECEIVED);
    }
    @Test void print_readonlyReceipt_rendersRequiredFieldsWithoutInternalDiagnosis()throws Exception{when(screens.detail(eq(1L),any())).thenReturn(detail(WarrantyTicketStatus.RECEIVED));mvc.perform(get("/staff/warranty/tickets/1/print").session(staff("WARRANTY_STAFF"))).andExpect(status().isOk()).andExpect(content().string(containsString("BIÊN NHẬN BẢO HÀNH"))).andExpect(content().string(containsString("SERIAL-1"))).andExpect(content().string(not(containsString("CHẨN ĐOÁN NỘI BỘ"))));}
    @Test void lookupStaff_fullHistoryAndInvoiceLink_renderWithoutPosPermission()throws Exception {
        var info=new StaffWarrantyView(2L,"SERIAL-1","Máy giặt","Khách Bí Mật","0909999999",9L,"HD-TEST",1L,LocalDate.of(2026,1,1),LocalDate.of(2027,1,1),"ACTIVE",List.of(new StaffWarrantyView.Ticket(detail(WarrantyTicketStatus.RECEIVED).ticket(),List.of())));
        when(screens.lookup(eq("SERIAL-1"),eq(""),any())).thenReturn(List.of(new Lookup(info,true,72,false)));
        mvc.perform(post("/staff/warranty/lookup").session(staff("WARRANTY_STAFF")).with(csrf()).param("serialNo","SERIAL-1"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Khách Bí Mật")))
            .andExpect(content().string(containsString("/staff/warranty/invoices/9"))).andExpect(content().string(containsString("Mở phiếu")));
    }
    @Test @com.dienmay.support.TestCase("TC-WAR-07")
    void receive_success_postRedirectGetAndFlash()throws Exception {
        when(service.receive(any(),any())).thenReturn(detail(WarrantyTicketStatus.RECEIVED).ticket());
        mvc.perform(post("/staff/warranty/tickets").session(staff("WARRANTY_STAFF")).with(csrf()).param("serialNo","SERIAL-1").param("warrantyId","2").param("issueDescription","Không lên nguồn"))
            .andExpect(redirectedUrl("/staff/warranty/tickets/1")).andExpect(flash().attribute("success","Đã tiếp nhận bảo hành."));
    }
    @Test void invoice_warrantyStaff_readOnlyView()throws Exception {
        when(screens.invoice(eq(9L),any())).thenReturn(new Invoice("HD-TEST","HQ","Khách","0909999999",LocalDateTime.of(2026,10,21,8,0),new java.math.BigDecimal("1000000"),List.of(new InvoiceLine("Máy giặt","SERIAL-1",1,new java.math.BigDecimal("1000000"),new java.math.BigDecimal("1000000")))));
        mvc.perform(get("/staff/warranty/invoices/9").session(staff("WARRANTY_STAFF"))).andExpect(status().isOk()).andExpect(content().string(containsString("1.000.000 đ"))).andExpect(content().string(containsString("Chỉ đọc")));
    }

}
