package com.dienmay.inventory.web;

import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.service.*;
import com.dienmay.inventory.security.CurrentInventoryUser;
import com.dienmay.inventory.serial.SerialStatus;
import com.dienmay.catalog.entity.SerialType;
import com.dienmay.common.exception.ResourceNotFoundException;
import com.dienmay.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import com.dienmay.support.InventoryTestUsers;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={GoodsReceiptController.class,SupplierController.class,InventoryStockController.class})
@Import({SecurityConfig.class,CurrentInventoryUser.class})
class InventoryWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean GoodsReceiptService receipts;
    @MockitoBean SupplierService suppliers;
    @MockitoBean InventoryChoiceService choices;
    @MockitoBean StockQueryService stock;

    @Test void receipts_cashier_isForbidden() throws Exception {
        mvc.perform(get("/inventory/receipts").with(user(InventoryTestUsers.user("CASHIER",1,1)))).andExpect(status().isForbidden());
        verifyNoInteractions(receipts);
    }
    @Test void receipts_warehouse_rendersList() throws Exception {
        when(receipts.search(any(),any(),any(),any(),any(),any())).thenReturn(Page.empty());
        mvc.perform(get("/inventory/receipts").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Phiếu nhập kho")));
    }
    @Test void create_missingCsrf_isForbidden() throws Exception {
        mvc.perform(post("/inventory/receipts").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isForbidden());
        verifyNoInteractions(receipts);
    }
    @Test void detail_otherBranch_returnsNotFound() throws Exception {
        when(receipts.getById(eq(7L),any())).thenThrow(new ResourceNotFoundException("Không tìm thấy phiếu nhập."));
        mvc.perform(get("/inventory/receipts/7").with(user(InventoryTestUsers.user("WAREHOUSE",2,1)))).andExpect(status().isNotFound());
    }
    @Test void newReceipt_warehouse_rendersDynamicRowsWithoutBranchPicker() throws Exception {
        when(choices.products(any())).thenReturn(List.of(new ReceiptProductChoice(1L,"PHONE","Điện thoại",true,SerialType.IMEI)));
        when(choices.branches(any())).thenReturn(List.of(new InventoryChoice(1L,"Chi nhánh A")));
        mvc.perform(get("/inventory/receipts/new").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("line-template")))
                .andExpect(content().string(containsString("data-serial=\"true\"")))
                .andExpect(content().string(containsString("Chi nhánh A")));
    }
    @Test void create_multipleErrors_redirectsAndPreservesRows() throws Exception {
        when(receipts.create(any(),any())).thenThrow(new ReceiptValidationException(List.of("Dòng 1: IMEI sai số kiểm tra","Dòng 2: Serial bị trùng")));
        var result=mvc.perform(post("/inventory/receipts").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf())
                .param("lines[0].productId","1").param("lines[0].serialText","MY-INPUT").param("lines[0].unitCost","10"))
                .andExpect(redirectedUrl("/inventory/receipts/new")).andReturn();
        mvc.perform(get("/inventory/receipts/new").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Dòng 1: IMEI sai số kiểm tra")))
                .andExpect(content().string(containsString("MY-INPUT")));
    }
    @Test void create_validForm_redirectsToDetail() throws Exception {
        when(receipts.create(any(),any())).thenReturn(7L);
        mvc.perform(post("/inventory/receipts").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))).with(csrf()))
                .andExpect(redirectedUrl("/inventory/receipts/7")).andExpect(flash().attributeExists("success"));
    }
    @Test void supplier_branchManager_isForbidden() throws Exception {
        mvc.perform(get("/inventory/suppliers").with(user(InventoryTestUsers.user("BRANCH_MANAGER",1,1)))).andExpect(status().isForbidden());
    }
    @Test void supplier_warehouse_rendersListAndForm() throws Exception {
        when(suppliers.search(any(),any(),any())).thenReturn(new PageImpl<>(List.of(new SupplierView(1L,"ABC","0900","Hà Nội",true))));
        mvc.perform(get("/inventory/suppliers").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isOk());
        mvc.perform(get("/inventory/suppliers/new").with(user(InventoryTestUsers.user("WAREHOUSE",1,1)))).andExpect(status().isOk());
    }
    @Test void stock_warehouse_rendersAllStatusColumns() throws Exception {
        when(stock.search(any(),any(),any(),any())).thenReturn(List.of(new StockRowView(1L,"A",1L,"SKU","Điện thoại","Danh mục",true,2,0,Map.of(SerialStatus.IN_STOCK,2L))));
        mvc.perform(get("/inventory/stock").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("IN_STOCK")));
    }
    @Test void detail_existingReceipt_rendersSerials() throws Exception {
        var summary=new ReceiptSummary(7L,"PN-20261014-00007","ABC",1L,"A","Người nhập",LocalDateTime.now());
        when(receipts.getById(eq(7L),any())).thenReturn(new ReceiptDetail(summary,"Ghi chú",List.of(new ReceiptItemView(1L,"SKU","Điện thoại",1,BigDecimal.TEN,List.of(new ReceiptSerialView(1L,"ABC-123",SerialStatus.IN_STOCK)))),BigDecimal.TEN));
        mvc.perform(get("/inventory/receipts/7").with(user(InventoryTestUsers.user("WAREHOUSE",1,1))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ABC-123")));
    }
}
