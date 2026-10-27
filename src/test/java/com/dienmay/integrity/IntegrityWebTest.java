package com.dienmay.integrity;

import com.dienmay.config.SecurityConfig;
import com.dienmay.support.ActiveEmployeeWebFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntegrityController.class) @Import(SecurityConfig.class)
class IntegrityWebTest extends ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;
    @MockitoBean DataIntegrityAuditService audits;
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"CASHIER","WAREHOUSE","WARRANTY_STAFF","BRANCH_MANAGER"})
    void page_nonAdminRole_isForbidden(String role)throws Exception{
        mvc.perform(get("/admin/integrity").with(user("staff").roles(role))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/integrity").with(user("staff").roles(role)).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(audits);
    }
    @Test void page_admin_isOk()throws Exception{mvc.perform(get("/admin/integrity").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());}
    @Test void page_anonymous_redirectsToStaffLogin()throws Exception{mvc.perform(get("/admin/integrity")).andExpect(redirectedUrlPattern("**/login"));}
    @Test void page_customerOnly_redirectsToStaffLogin()throws Exception{
        var session=new org.springframework.mock.web.MockHttpSession();var principal=org.springframework.security.core.userdetails.User.withUsername("customer").password("hash").roles("CUSTOMER").build();
        session.setAttribute(SecurityConfig.CUSTOMER_CONTEXT,new org.springframework.security.core.context.SecurityContextImpl(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities())));
        mvc.perform(get("/admin/integrity").session(session)).andExpect(redirectedUrlPattern("**/login"));verifyNoInteractions(audits);
    }
    @Test void audit_missingCsrf_isForbidden()throws Exception{mvc.perform(post("/admin/integrity").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());verifyNoInteractions(audits);}
    @Test void audit_legacySnapshot_rendersUnknownInsteadOfPass()throws Exception{
        when(audits.audit()).thenReturn(new DataIntegrityAuditService.Report(java.time.LocalDateTime.of(2026,10,23,9,0),1.0,List.of(new WarrantySnapshotAuditService.Check("INV-11","Snapshot",0,2,1.0,List.of(new WarrantySnapshotAuditService.Finding("invoice_item",1,1,"UNKNOWN"))))));
        mvc.perform(post("/admin/integrity").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk()).andExpect(content().string(containsString("Chưa đủ dữ liệu")));
    }
}
