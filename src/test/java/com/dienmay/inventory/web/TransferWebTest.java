package com.dienmay.inventory.web;

import com.dienmay.config.SecurityConfig;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.TransferStatus;
import com.dienmay.inventory.security.CurrentInventoryUser;
import com.dienmay.inventory.service.*;
import com.dienmay.support.InventoryTestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TransferController.class,SerialLookupController.class})
@Import({SecurityConfig.class,CurrentInventoryUser.class})
class TransferWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean TransferService transfers;
    @MockitoBean SerialLookupService lookup;
    @ParameterizedTest @ValueSource(strings={"CASHIER","WARRANTY_STAFF"})
    void transfers_unauthorizedRole_returns403(String role) throws Exception {
        mvc.perform(get("/inventory/transfers").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isForbidden());verifyNoInteractions(transfers);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","BRANCH_MANAGER","WAREHOUSE"})
    void transfers_inventoryRole_rendersList(String role) throws Exception {
        when(transfers.search(any(),any(),any(),any(),any())).thenReturn(Page.empty());
        mvc.perform(get("/inventory/transfers").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Phiếu chuyển kho")));
    }
    @ParameterizedTest @ValueSource(strings={"ship","receive","cancel"})
    void action_missingCsrf_returns403(String action) throws Exception {
        mvc.perform(post("/inventory/transfers/7/"+action).with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(transfers);
    }
    @Test void create_missingCsrf_returns403() throws Exception {
        mvc.perform(post("/inventory/transfers").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(transfers);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","BRANCH_MANAGER","WAREHOUSE","WARRANTY_STAFF"})
    void lookup_allowedRoles_rendersSearch(String role) throws Exception {
        mvc.perform(get("/inventory/serials").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Tra cứu vòng đời Serial")));
    }
    @Test void lookup_cashier_returns403() throws Exception {
        mvc.perform(get("/inventory/serials").param("serialNo","SERIAL-001").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(lookup);
    }
    @Test void lookup_warrantyPost_returns403() throws Exception {
        mvc.perform(post("/inventory/serials").with(user(InventoryTestUsers.user("WARRANTY_STAFF",1,1))).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void create_businessErrors_preservesInputAfterRedirect() throws Exception {
        when(transfers.createDraft(any(),any())).thenThrow(new TransferValidationException(List.of("Serial không còn IN_STOCK")));
        var result=mvc.perform(post("/inventory/transfers").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf()).param("serialText","MY-SERIAL"))
            .andExpect(redirectedUrl("/inventory/transfers/new")).andReturn();
        when(transfers.branchChoices(any())).thenReturn(List.of(new InventoryChoice(1L,"A"),new InventoryChoice(2L,"B")));
        mvc.perform(get("/inventory/transfers/new").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("MY-SERIAL"))).andExpect(content().string(containsString("Serial không còn IN_STOCK")));
    }
    @Test void new_admin_rendersBranchSelectorsAndQuantityTemplate() throws Exception {
        when(transfers.branchChoices(any())).thenReturn(List.of(new InventoryChoice(1L,"A"),new InventoryChoice(2L,"B")));
        when(transfers.quantityChoices(any())).thenReturn(List.of(new InventoryChoice(3L,"Cáp sạc")));
        mvc.perform(get("/inventory/transfers/new").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"fromBranchId\""))).andExpect(content().string(containsString("line-template"))).andExpect(content().string(containsString("Cáp sạc")));
    }
    @Test void action_businessFailure_flashesMessageWithoutStackTrace() throws Exception {
        doThrow(new com.dienmay.common.exception.BusinessException("Serial đã xuất" )).when(transfers).ship(eq(7L),any());
        mvc.perform(post("/inventory/transfers/7/ship").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf()))
            .andExpect(redirectedUrl("/inventory/transfers/7")).andExpect(flash().attribute("error","Serial đã xuất"));
    }
    @Test void detail_received_hasNoWriteButtons() throws Exception {
        TransferSummary summary=new TransferSummary(7L,"CK-20261014-00007",1L,"A",2L,"B",TransferStatus.RECEIVED,LocalDateTime.now());
        when(transfers.getById(eq(7L),any())).thenReturn(new TransferDetail(summary,"Note","Admin","Admin",LocalDateTime.now(),"Kho B",LocalDateTime.now(),List.of(new TransferItemView(1L,"SKU","Máy","SERIAL-001",1)),false,false,false));
        mvc.perform(get("/inventory/transfers/7").with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("/7/ship")))).andExpect(content().string(not(containsString("/7/receive")))).andExpect(content().string(not(containsString("/7/cancel"))));
    }
}
