package com.dienmay.reports;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.organization.entity.*;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.sales.security.SalesActor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportRulesTest {
    Clock clock=Clock.fixed(Instant.parse("2026-02-01T17:00:00Z"),ZoneOffset.UTC);
    @Test void range_default_30DaysVietnamTimezone(){var r=ReportRange.resolve(null,clock);assertThat(r.to()).isEqualTo(LocalDate.of(2026,2,2));assertThat(r.days()).isEqualTo(30);assertThat(r.previous().days()).isEqualTo(30);assertThat(r.previous().to()).isEqualTo(r.from().minusDays(1));}
    @Test @com.dienmay.support.TestCase("TC-RPT-12")
    void range_reversed_rejected(){assertThatThrownBy(()->ReportRange.resolve(new ReportRange(LocalDate.of(2026,2,3),LocalDate.of(2026,2,2)),clock)).hasMessageContaining("Ngày bắt đầu");}
    @Test @com.dienmay.support.TestCase("TC-RPT-12")
    void range_moreThan366_rejected(){assertThatThrownBy(()->ReportRange.resolve(new ReportRange(LocalDate.of(2025,1,1),LocalDate.of(2026,1,2)),clock)).hasMessageContaining("366");}
    @Test void range_exact366AndHalfOpen_valid(){var r=ReportRange.resolve(new ReportRange(LocalDate.of(2024,1,1),LocalDate.of(2024,12,31)),clock);assertThat(r.days()).isEqualTo(366);assertThat(r.endExclusive()).isEqualTo(LocalDateTime.of(2025,1,1,0,0));}
    @Test @com.dienmay.support.TestCase("TC-RPT-06")
    void periods_missingDaysAndMonths_filledWithZero(){var r=new ReportRange(LocalDate.of(2026,1,31),LocalDate.of(2026,2,2));var point=new RevenuePoint(LocalDate.of(2026,2,1),new Totals(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,1,0,1));var result=ReportQueryService.fillPeriods(r,Granularity.DAY,List.of(point));assertThat(result).hasSize(3);assertThat(result.getFirst().totals()).isEqualTo(Totals.zero());assertThat(result.get(1)).isEqualTo(point);assertThat(ReportQueryService.fillPeriods(r,Granularity.MONTH,List.of())).extracting(RevenuePoint::period).containsExactly(LocalDate.of(2026,1,1),LocalDate.of(2026,2,1));}
    @Test @com.dienmay.support.TestCase("TC-RPT-11")
    void comparison_zeroPrevious_percentNullAndNegativePreviousDirectionPreserved(){assertThat(ReportQueryService.delta(BigDecimal.TEN,BigDecimal.ZERO).percent()).isNull();assertThat(ReportQueryService.delta(BigDecimal.TEN,new BigDecimal("-10")).percent()).isEqualByComparingTo("200");}
    ReportAccess access(String role){var employees=mock(EmployeeRepository.class);var employee=new Employee();var branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);employee.setBranch(branch);var r=new Role();ReflectionTestUtils.setField(r,"code",role);employee.setRole(r);when(employees.findSalesActor(2L)).thenReturn(Optional.of(employee));return new ReportAccess(employees);}
    SalesActor actor(String role){return new SalesActor(2L,1L,Set.of("ROLE_"+role));}
    @Test @com.dienmay.support.TestCase("TC-RPT-13")
    void scope_managerOtherBranch_notFound(){assertThatThrownBy(()->access("BRANCH_MANAGER").resolve(ReportScope.branch(9L),actor("BRANCH_MANAGER"),ReportAccess.Kind.FINANCIAL)).isInstanceOf(com.dienmay.common.exception.ResourceNotFoundException.class);}
    @Test void scope_managerChainRequest_forcedOwnBranch(){assertThat(access("BRANCH_MANAGER").resolve(ReportScope.chain(),actor("BRANCH_MANAGER"),ReportAccess.Kind.FINANCIAL).branchId()).isEqualTo(1L);}
    @Test @com.dienmay.support.TestCase("TC-RPT-14")
    @com.dienmay.support.TestCase("TC-RPT-15")
    void scope_warehouse_allowsInventoryDeniesFinancial(){assertThat(access("WAREHOUSE").resolve(null,actor("WAREHOUSE"),ReportAccess.Kind.INVENTORY).branchId()).isEqualTo(1L);assertThatThrownBy(()->access("WAREHOUSE").resolve(null,actor("WAREHOUSE"),ReportAccess.Kind.FINANCIAL)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void scope_adminChainAndAnyBranch_allowed(){var access=access("ADMIN");assertThat(access.resolve(null,actor("ADMIN"),ReportAccess.Kind.CHAIN).branchId()).isNull();assertThat(access.resolve(ReportScope.branch(7L),actor("ADMIN"),ReportAccess.Kind.INVENTORY).branchId()).isEqualTo(7L);}
    @Test @com.dienmay.support.TestCase("TC-RPT-14")
    void scope_cashierAndForgedRole_denied(){assertThatThrownBy(()->access("CASHIER").resolve(null,actor("CASHIER"),ReportAccess.Kind.INVENTORY)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);assertThatThrownBy(()->access("WAREHOUSE").resolve(null,actor("ADMIN"),ReportAccess.Kind.FINANCIAL)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
    @Test void dto_publicReport_excludesCustomerPiiAndCost(){for(Class<?> dto:ReportDtos.class.getDeclaredClasses())if(dto.isRecord())assertThat(Arrays.stream(dto.getRecordComponents()).map(c->c.getName())).noneMatch(n->n.toLowerCase().contains("phone")||n.toLowerCase().contains("customer")||n.toLowerCase().contains("cost"));}
    @Test void limits_invalidTopPageAndThreshold_rejected(){
        var access=mock(ReportAccess.class);when(access.resolve(any(),any(),any())).thenReturn(ReportScope.chain());
        var service=new ReportQueryService(access,mock(ReportReadRepository.class),new ReportProperties(),clock);
        assertThatThrownBy(()->service.topProducts(null,null,51,ProductOrder.QUANTITY,null)).hasMessageContaining("50");
        assertThatThrownBy(()->service.lowStock(null,-1,null)).hasMessageContaining("âm");
        assertThatThrownBy(()->service.inventoryStock(null,null,null,org.springframework.data.domain.PageRequest.of(0,101),null)).hasMessageContaining("100");
        assertThatThrownBy(()->service.inventoryStock(null,null,null,org.springframework.data.domain.PageRequest.of(0,10,org.springframework.data.domain.Sort.by("cost_price")),null)).hasMessageContaining("không hợp lệ");
    }

}
