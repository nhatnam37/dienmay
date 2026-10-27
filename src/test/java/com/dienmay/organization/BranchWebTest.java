package com.dienmay.organization;

import com.dienmay.config.SecurityConfig;
import com.dienmay.organization.dto.*;
import com.dienmay.organization.service.BranchService;
import com.dienmay.organization.web.BranchController;
import com.dienmay.support.InventoryTestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BranchController.class)
@Import(SecurityConfig.class)
class BranchWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;
    @MockitoBean BranchService branches;
    @Test void list_warehouse_isForbidden() throws Exception {mvc.perform(get("/admin/organization/branches").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(branches);}
    @Test void listAndNew_admin_renderBranchManagement() throws Exception {
        when(branches.list()).thenReturn(List.of(new BranchView(1L,"HQ","Trụ sở chính",null,null,true)));
        mvc.perform(get("/admin/organization/branches").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Thêm chi nhánh")));
        mvc.perform(get("/admin/organization/branches/new").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Lưu chi nhánh")));
    }
    @Test void create_missingCsrf_isForbidden() throws Exception {mvc.perform(post("/admin/organization/branches").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(branches);}
    @Test void create_validForm_redirectsToList() throws Exception {
        mvc.perform(post("/admin/organization/branches").with(user(InventoryTestUsers.user("ADMIN",1,1))).with(csrf()).param("code","CN-HCM").param("name","Hồ Chí Minh").param("active","true"))
            .andExpect(redirectedUrl("/admin/organization/branches")).andExpect(flash().attributeExists("success"));verify(branches).create(any());
    }
    @Test void create_duplicateCode_preservesFormAndShowsError() throws Exception {
        when(branches.create(any())).thenThrow(new com.dienmay.common.exception.BusinessException("Mã chi nhánh đã tồn tại."));
        var result=mvc.perform(post("/admin/organization/branches").with(user(InventoryTestUsers.user("ADMIN",1,1))).with(csrf()).param("code","HQ").param("name","Tên đã gõ"))
            .andExpect(redirectedUrl("/admin/organization/branches/new")).andReturn();
        mvc.perform(get("/admin/organization/branches/new").with(user(InventoryTestUsers.user("ADMIN",1,1))).flashAttrs(result.getFlashMap())).andExpect(status().isOk()).andExpect(content().string(containsString("Mã chi nhánh đã tồn tại."))).andExpect(response -> org.assertj.core.api.Assertions.assertThat(org.springframework.web.util.HtmlUtils.htmlUnescape(response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))).contains("Tên đã gõ"));
    }
}
