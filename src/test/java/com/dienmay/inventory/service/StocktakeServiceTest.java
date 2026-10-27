package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StocktakeServiceTest {
    @Mock StocktakeRepository takes;@Mock StocktakeLineRepository lines;@Mock ProductRepository products;
    @Mock InventoryProductQueries choices;@Mock BranchRepository branches;@Mock EmployeeRepository employees;
    @Mock SerialItemRepository serials;@Mock SerialStateService states;@Mock StockService stock;@Mock StocktakeSnapshotReader snapshots;
    StocktakeService service;
    InventoryActor manager=new InventoryActor(1L,1L,Set.of("ROLE_BRANCH_MANAGER"));
    InventoryActor warehouse=new InventoryActor(1L,1L,Set.of("ROLE_WAREHOUSE"));
    Stocktake take;
    @BeforeEach void setup(){service=new StocktakeService(takes,lines,products,choices,branches,employees,serials,states,stock,snapshots,new StocktakeComparison(),new SerialNumberRules(),new InventoryAccess(),java.time.Clock.fixed(java.time.Instant.parse("2026-10-23T02:00:00Z"),java.time.ZoneId.of("Asia/Ho_Chi_Minh")));take=new Stocktake();ReflectionTestUtils.setField(take,"id",7L);Branch branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);take.setBranch(branch);}
    void header(){when(takes.findForUpdate(7L)).thenReturn(Optional.of(take));}
    StocktakeLine line(String number,int system,Integer counted,String note){Product p=new Product();ReflectionTestUtils.setField(p,"id",2L);p.setSku("SKU-2");StocktakeLine l=new StocktakeLine();l.setProduct(p);l.setSerialNo(number);l.setSystemQty(system);l.setCountedQty(counted);l.setSnapshotVersion(0L);l.setNote(note);return l;}
    void counted(StocktakeLine l){header();when(employees.findById(1L)).thenReturn(Optional.of(new Employee()));when(lines.findAllByStocktakeIdOrderByIdAsc(7L)).thenReturn(List.of(l));when(snapshots.read(eq(1L),anySet(),eq(true))).thenReturn(l.getSerialNo()!=null&&l.getSystemQty()==0?List.of():List.of(new StocktakeSnapshotRow(2L,"SKU-2","Máy",l.getSerialNo(),l.getSerialNo()==null?null:SerialStatus.IN_STOCK,l.getSystemQty(),0,0L)));}
    @Test void close_warehouse_isForbidden(){header();assertThatThrownBy(()->service.close(7L,warehouse)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);verifyNoInteractions(states,stock);}
    @Test void close_otherBranch_returns404(){header();assertThatThrownBy(()->service.close(7L,new InventoryActor(1L,9L,Set.of("ROLE_BRANCH_MANAGER")))) .isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(states,stock);}
    @Test void close_alreadyClosed_rejectsWithoutAdjusting(){header();take.setStatus(StocktakeStatus.CLOSED);assertThatThrownBy(()->service.close(7L,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("đã chốt");verifyNoInteractions(states,stock);}
    @Test void close_countNotEntered_rejects(){header();when(employees.findById(1L)).thenReturn(Optional.of(new Employee()));when(lines.findAllByStocktakeIdOrderByIdAsc(7L)).thenReturn(List.of(line(null,10,null,null)));assertThatThrownBy(()->service.close(7L,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("Chưa nhập đủ");verifyNoInteractions(states,stock);}
    @Test void close_missingReason_rejectsBeforeTransitions(){counted(line("SERIAL-1",1,0," "));assertThatThrownBy(()->service.close(7L,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("lý do");verifyNoInteractions(states,stock);}
    @Test void close_missingSerial_callsStateMachineWithStocktakeReference(){counted(line("SERIAL-1",1,0,"Mất khi kiểm kê"));SerialItem serial=new SerialItem();ReflectionTestUtils.setField(serial,"id",10L);serial.setSerialNo("SERIAL-1");when(serials.findAllBySerialNoIn(anySet())).thenReturn(List.of(serial));when(serials.findByIdForUpdate(10L)).thenReturn(Optional.of(serial));service.close(7L,manager);verify(states).transition(eq(serial),eq(SerialStatus.DAMAGED),argThat(ctx->ctx.refType().equals("STOCKTAKE")&&ctx.refId().equals(7L)&&ctx.note().equals("Mất khi kiểm kê")));assertThat(take.getStatus()).isEqualTo(StocktakeStatus.CLOSED);assertThat(take.getClosedBy()).isNotNull();}
    @Test void close_unknownSerial_neverChangesSerialOrStock(){counted(line("UNKNOWN",0,1,"Không có trong DB"));service.close(7L,manager);verifyNoInteractions(states,stock,serials);}
    @Test void close_quantityDeficit_delegatesDecrease(){counted(line(null,10,7,"Đếm thiếu"));service.close(7L,manager);verify(stock).decrease(eq(1L),eq(2L),eq(3),argThat(ctx->ctx.refType().equals("STOCKTAKE")));}
    @Test void close_quantitySurplus_delegatesIncrease(){counted(line(null,10,12,null));service.close(7L,manager);verify(stock).increase(eq(1L),eq(2L),eq(2),any());}
    @Test void close_stockVersionChanged_rejectsBeforeAdjusting(){header();when(employees.findById(1L)).thenReturn(Optional.of(new Employee()));when(lines.findAllByStocktakeIdOrderByIdAsc(7L)).thenReturn(List.of(line(null,10,7,"Thiếu")));when(snapshots.read(eq(1L),anySet(),eq(true))).thenReturn(List.of(new StocktakeSnapshotRow(2L,"SKU","Máy",null,null,10,0,2L)));assertThatThrownBy(()->service.close(7L,manager)).isInstanceOf(BusinessException.class).hasMessageContaining("Đối chiếu lại");verifyNoInteractions(states,stock);}
}
