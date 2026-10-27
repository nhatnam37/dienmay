package com.dienmay.sales;

import com.dienmay.sales.security.*;
import com.dienmay.sales.service.PosQueryService;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.mapper.CheckoutMapper;
import com.dienmay.sales.entity.*;
import com.dienmay.warranty.repository.WarrantyRepository;
import com.dienmay.inventory.service.StockService;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.common.exception.ResourceNotFoundException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PosQueryServiceTest {
    @Mock SalesAccess access;@Mock InvoiceRepository invoices;@Mock InvoiceItemRepository items;@Mock PaymentRepository payments;@Mock WarrantyRepository warranties;
    @Mock PosProductRepository products;@Mock StockService stock;@Mock BranchRepository branches;
    PosQueryService service;SalesActor actor;Invoice invoice;
    @BeforeEach void setup(){service=new PosQueryService(access,invoices,items,payments,warranties,new CheckoutMapper(),products,stock,branches,Clock.fixed(Instant.parse("2026-10-14T05:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh")));}
    void role(String code){
        actor=new SalesActor(1L,1L,Set.of("ROLE_"+code));Role role=new Role();ReflectionTestUtils.setField(role,"code",code);Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);Employee employee=new Employee();employee.setRole(role);employee.setBranch(branch);ReflectionTestUtils.setField(employee,"id",1L);when(access.validate(1L,actor)).thenReturn(new SalesAccess.Scope(employee,!code.equals("CASHIER")));
    }
    void invoice(long branchId,long cashierId,LocalDate date){invoice=new Invoice();Branch b=new Branch();ReflectionTestUtils.setField(b,"id",branchId);Employee e=new Employee();ReflectionTestUtils.setField(e,"id",cashierId);invoice.setBranch(b);invoice.setCashier(e);invoice.setCreatedAt(date.atStartOfDay());when(invoices.findById(7L)).thenReturn(Optional.of(invoice));}
    @Test @com.dienmay.support.TestCase("TC-POS-18")
    void detail_managerOtherBranch_throws404(){role("BRANCH_MANAGER");invoice(2,1,LocalDate.of(2026,10,14));assertThatThrownBy(()->service.detail(7L,actor)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(items,payments,warranties);}
    @Test void detail_cashierOtherEmployee_throws404(){role("CASHIER");invoice(1,2,LocalDate.of(2026,10,14));assertThatThrownBy(()->service.detail(7L,actor)).isInstanceOf(ResourceNotFoundException.class);}
    @Test void detail_cashierPreviousDay_throws404(){role("CASHIER");invoice(1,1,LocalDate.of(2026,10,13));assertThatThrownBy(()->service.detail(7L,actor)).isInstanceOf(ResourceNotFoundException.class);}
    @Test void detail_adminOtherBranch_isAllowed(){role("ADMIN");invoice(2,2,LocalDate.of(2026,10,13));when(items.findAllByInvoiceIdOrderByIdAsc(7L)).thenReturn(List.of());when(payments.findAllByInvoiceIdOrderByIdAsc(7L)).thenReturn(List.of());when(warranties.findAllByInvoiceItemInvoiceId(7L)).thenReturn(List.of());assertThat(service.detail(7L,actor).invoice().branchId()).isEqualTo(2);}
    @Test void search_cashierForgedFilters_forcesOwnBranchEmployeeAndToday(){
        role("CASHIER");when(invoices.search(any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(Page.empty());
        service.search(99L,LocalDate.of(2020,1,1),LocalDate.of(2030,1,1),null,null,null,0,actor);
        verify(invoices).search(eq(1L),eq(1L),eq(LocalDate.of(2026,10,14).atStartOfDay()),eq(LocalDate.of(2026,10,15).atStartOfDay()),isNull(),isNull(),isNull(),any());
    }
    @Test void search_managerForgedBranch_forcesOwnBranch(){role("BRANCH_MANAGER");when(invoices.search(any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(Page.empty());service.search(99L,null,null,null,null,null,0,actor);verify(invoices).search(eq(1L),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),any());}
    @Test void search_adminBranch_keepsSelectedBranch(){role("ADMIN");when(invoices.search(any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(Page.empty());service.search(99L,null,null,null,null,null,0,actor);verify(invoices).search(eq(99L),isNull(),isNull(),isNull(),isNull(),isNull(),isNull(),any());}
}
