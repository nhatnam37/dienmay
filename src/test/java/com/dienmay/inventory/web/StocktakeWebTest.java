package com.dienmay.inventory.web;

import com.dienmay.config.SecurityConfig;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.StocktakeStatus;
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
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StocktakeController.class)
@Import({SecurityConfig.class,CurrentInventoryUser.class})
class StocktakeWebTest extends com.dienmay.support.ActiveEmployeeWebFixture {
    @Autowired MockMvc mvc;@MockitoBean StocktakeService takes;@MockitoBean InventoryChoiceService choices;
    StocktakeDetail detail(boolean canClose,boolean counted){var s=new StocktakeSummary(7L,"KK-20261014-00007",1L,"HQ",StocktakeStatus.OPEN,"Nhân viên",LocalDateTime.now());return new StocktakeDetail(s,null,null,List.of(new StocktakeLineView(2L,"SKU","Máy","ABC-123",counted?0:null,1,"Mất máy")),canClose,counted);}
    @ParameterizedTest @ValueSource(strings={"CASHIER","WARRANTY_STAFF"})
    void list_nonInventoryRoles_returns403(String role)throws Exception{mvc.perform(get("/inventory/stocktakes").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isForbidden());verifyNoInteractions(takes);}
    @ParameterizedTest @ValueSource(strings={"ADMIN","BRANCH_MANAGER","WAREHOUSE"})
    void list_inventoryRoles_rendersList(String role)throws Exception{when(takes.search(any(),any(),any(),any())).thenReturn(Page.empty());mvc.perform(get("/inventory/stocktakes").with(user(InventoryTestUsers.user(role,1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("Mở phiếu kiểm kê")));}
    @ParameterizedTest @ValueSource(strings={"count","close","reconcile"})
    void post_missingCsrf_returns403(String action)throws Exception{mvc.perform(post("/inventory/stocktakes/7/"+action).with(user(InventoryTestUsers.user("ADMIN",1,1)))).andExpect(status().isForbidden());verifyNoInteractions(takes);}
    @Test void open_missingCsrf_returns403()throws Exception{mvc.perform(post("/inventory/stocktakes").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isForbidden());}
    @Test void close_warehouseEvenWithCsrf_returns403()throws Exception{mvc.perform(post("/inventory/stocktakes/7/close").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(takes);}
    @Test void open_valid_redirectsToCountForm()throws Exception{when(takes.open(eq(1L),any())).thenReturn(7L);mvc.perform(post("/inventory/stocktakes").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf()).param("branchId","1")).andExpect(redirectedUrl("/inventory/stocktakes/7/count"));}
    @Test void detail_warehouse_hidesCloseButton()throws Exception{when(takes.getById(eq(7L),any())).thenReturn(detail(false,true));mvc.perform(get("/inventory/stocktakes/7").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isOk()).andExpect(content().string(not(containsString("/7/close"))));}
    @Test void detail_managerWithCount_showsCloseButton()throws Exception{when(takes.getById(eq(7L),any())).thenReturn(detail(true,true));mvc.perform(get("/inventory/stocktakes/7").with(user(InventoryTestUsers.user("BRANCH_MANAGER",1,1)))).andExpect(status().isOk()).andExpect(content().string(containsString("/7/close")));}
    @Test void save_validationFailure_preservesSerialInputAfterRedirect()throws Exception{doThrow(new StocktakeValidationException(List.of("Serial trùng"))).when(takes).saveCount(eq(7L),any(),any());var result=mvc.perform(post("/inventory/stocktakes/7/count").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf()).param("lines[0].productId","2").param("lines[0].serialText","MY-SERIAL")).andExpect(redirectedUrl("/inventory/stocktakes/7/count")).andReturn();when(takes.getById(eq(7L),any())).thenReturn(detail(false,false));when(takes.productChoices(any())).thenReturn(List.of(new StocktakeProductChoice(2L,"SKU","Máy",true)));mvc.perform(get("/inventory/stocktakes/7/count").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).flashAttrs(result.getFlashMap())).andExpect(status().isOk()).andExpect(content().string(containsString("MY-SERIAL"))).andExpect(content().string(containsString("Serial trùng")));}
    @Test void close_businessError_flashesWithoutStackTrace()throws Exception{doThrow(new com.dienmay.common.exception.BusinessException("Tồn kho đã thay đổi")).when(takes).close(eq(7L),any());mvc.perform(post("/inventory/stocktakes/7/close").with(user(InventoryTestUsers.user("ADMIN",1,1))).with(csrf())).andExpect(redirectedUrl("/inventory/stocktakes/7")).andExpect(flash().attribute("error","Tồn kho đã thay đổi"));}
}
