package com.dienmay.warranty;
import com.dienmay.warranty.service.*;
import com.dienmay.warranty.entity.*;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.mapper.WarrantyTicketMapper;
import com.dienmay.warranty.repository.*;
import com.dienmay.warranty.security.WarrantyAccess;
import com.dienmay.inventory.serial.*;
import com.dienmay.sales.service.CheckoutNormalizer;
import com.dienmay.organization.repository.EmployeeRepository;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class WarrantyQueryServiceTest {
    WarrantyRepository warranties=mock(WarrantyRepository.class);WarrantyTicketRepository tickets=mock(WarrantyTicketRepository.class);
    WarrantyQueryService service=new WarrantyQueryService(warranties,tickets,mock(WarrantyTicketHistoryRepository.class),new WarrantyStatusPolicy(Clock.systemUTC()),new SerialNumberRules(),mock(CheckoutNormalizer.class),mock(WarrantyAccess.class),new WarrantyTicketMapper(),mock(EmployeeRepository.class));
    @Test @com.dienmay.support.TestCase("TC-WAR-04")
    void publicLookup_unknownUnsoldVoided_sameEmptyResult(){when(warranties.findAllBySerialItemSerialNoOrderByCreatedAtDescIdDesc("TEST-123")).thenReturn(List.of());var unknown=service.publicLookup("test-123");var serial=new SerialItem();org.springframework.test.util.ReflectionTestUtils.setField(serial,"status",SerialStatus.IN_STOCK);var w=new Warranty();w.setSerialItem(serial);when(warranties.findAllBySerialItemSerialNoOrderByCreatedAtDescIdDesc("TEST-123")).thenReturn(List.of(w));assertThat(service.publicLookup("TEST-123")).isEqualTo(unknown);org.springframework.test.util.ReflectionTestUtils.setField(serial,"status",SerialStatus.SOLD);w.setStatus(WarrantyStatus.VOIDED);assertThat(service.publicLookup("TEST-123")).isEqualTo(unknown);}
    @Test @com.dienmay.support.TestCase("TC-WAR-03")
    void publicDtos_structure_hasOnlyApprovedFields(){assertThat(Arrays.stream(PublicWarrantyView.class.getRecordComponents()).map(c->c.getName())).containsExactly("productName","startDate","endDate","valid","daysRemaining","repairStatus");assertThat(Arrays.stream(PublicTicketView.class.getRecordComponents()).map(c->c.getName())).containsExactly("status","receivedAt","completedAt","returnedAt","timeline");}
    @Test @com.dienmay.support.TestCase("TC-WAR-06")
    void publicTrack_missingOnePart_returnsNothing(){assertThat(service.publicTrack(null,"TEST-123")).isEmpty();assertThat(service.publicTrack("BH-123",null)).isEmpty();verifyNoInteractions(tickets);}
}
