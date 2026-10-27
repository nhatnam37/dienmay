package com.dienmay.warranty;
import com.dienmay.warranty.service.*;
import com.dienmay.warranty.entity.*;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.mapper.WarrantyTicketMapper;
import com.dienmay.warranty.repository.*;
import com.dienmay.warranty.security.WarrantyAccess;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.organization.entity.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class WarrantyTicketServiceTest {
    @Mock WarrantyRepository warranties;@Mock WarrantyTicketRepository tickets;@Mock WarrantyTicketHistoryRepository histories;
    @Mock SerialItemRepository serials;@Mock ProductRepository products;@Mock SerialStateService states;@Mock WarrantyAccess access;
    WarrantyTicketService service;Employee employee;Warranty warranty;SerialItem serial;SalesActor actor;
    @BeforeEach void fixture(){
        var clock=Clock.fixed(Instant.parse("2026-10-21T01:00:00Z"),ZoneOffset.UTC);
        service=new WarrantyTicketService(warranties,tickets,histories,serials,products,states,access,new SerialNumberRules(),new WarrantyStatusPolicy(clock),new WarrantyTicketMapper(),clock);
        var branch=new Branch();ReflectionTestUtils.setField(branch,"id",1L);var role=new Role();ReflectionTestUtils.setField(role,"code","WARRANTY_STAFF");
        employee=new Employee();ReflectionTestUtils.setField(employee,"id",1L);employee.setBranch(branch);employee.setRole(role);
        actor=new SalesActor(1L,1L,Set.of("ROLE_WARRANTY_STAFF"));
        serial=new SerialItem();ReflectionTestUtils.setField(serial,"id",2L);ReflectionTestUtils.setField(serial,"status",SerialStatus.SOLD);serial.setSerialNo("TEST-123");
        warranty=new Warranty();ReflectionTestUtils.setField(warranty,"id",3L);warranty.setSerialItem(serial);warranty.setEndDate(LocalDate.of(2027,1,1));
        lenient().when(access.validate(actor)).thenReturn(employee);lenient().when(warranties.findSerialIdentity(3L)).thenReturn(Optional.of(2L));
        lenient().when(serials.findProductIdentity(2L)).thenReturn(Optional.of(4L));lenient().when(products.findForUpdate(4L)).thenReturn(Optional.of(new Product()));
        lenient().when(serials.findByIdForUpdate(2L)).thenReturn(Optional.of(serial));lenient().when(warranties.findById(3L)).thenReturn(Optional.of(warranty));
        lenient().when(tickets.saveAndFlush(any())).thenAnswer(inv->{var t=inv.<WarrantyTicket>getArgument(0);ReflectionTestUtils.setField(t,"id",5L);ReflectionTestUtils.setField(t,"version",0L);return t;});
    }
    ReceiveRequest request(){return new ReceiveRequest(3L,null,"Không lên nguồn",null);}
    @org.junit.jupiter.api.DisplayName("Máy chưa bán không đủ điều kiện bảo hành")
    @Test @com.dienmay.support.TestCase("TC-WAR-08")
    void receive_notSold_rejected(){ReflectionTestUtils.setField(serial,"status",SerialStatus.IN_STOCK);assertThatThrownBy(()->service.receive(request(),actor)).hasMessageContaining("SOLD");verify(tickets,never()).saveAndFlush(any());}
    @org.junit.jupiter.api.DisplayName("Hồ sơ đã vô hiệu không được tiếp nhận")
    @Test @com.dienmay.support.TestCase("TC-WAR-08")
    void receive_voided_rejected(){warranty.setStatus(WarrantyStatus.VOIDED);assertThatThrownBy(()->service.receive(request(),actor)).hasMessageContaining("VOIDED");}
    @org.junit.jupiter.api.DisplayName("Không mở phiếu trùng khi đang sửa")
    @Test @com.dienmay.support.TestCase("TC-WAR-08")
    @com.dienmay.support.TestCase("TC-WAR-17")
    void receive_openTicket_rejected(){when(tickets.existsOpen(2L)).thenReturn(true);assertThatThrownBy(()->service.receive(request(),actor)).hasMessageContaining("đang mở");}
    @org.junit.jupiter.api.DisplayName("Tiếp nhận phải có mô tả lỗi")
    @Test @com.dienmay.support.TestCase("TC-WAR-08")
    void receive_missingIssue_rejected(){assertThatThrownBy(()->service.receive(new ReceiveRequest(3L,null,"  ",null),actor)).hasMessageContaining("Mô tả lỗi");}
    @org.junit.jupiter.api.DisplayName("Nhân viên không được nhận máy hết bảo hành")
    @Test @com.dienmay.support.TestCase("TC-WAR-09")
    void receive_expiredStaff_rejected(){warranty.setEndDate(LocalDate.of(2020,1,1));assertThatThrownBy(()->service.receive(request(),actor)).hasMessageContaining("hết hạn");}
    @org.junit.jupiter.api.DisplayName("Quản lý có lý do được nhận ngoài bảo hành")
    @Test @com.dienmay.support.TestCase("TC-WAR-10")
    void receive_expiredManagerWithReason_accepted(){warranty.setEndDate(LocalDate.of(2020,1,1));ReflectionTestUtils.setField(employee.getRole(),"code","BRANCH_MANAGER");var result=service.receive(new ReceiveRequest(3L,null,"Lỗi","Ngoại lệ được duyệt"),actor);assertThat(result.outOfWarranty()).isTrue();assertThat(result.branchId()).isEqualTo(1L);verify(states).transition(eq(serial),eq(SerialStatus.IN_WARRANTY_REPAIR),any());}
    @org.junit.jupiter.api.DisplayName("Quản lý nhận ngoài bảo hành vẫn phải có lý do")
    @Test void receive_expiredManagerWithoutReason_rejected(){warranty.setEndDate(LocalDate.of(2020,1,1));ReflectionTestUtils.setField(employee.getRole(),"code","BRANCH_MANAGER");assertThatThrownBy(()->service.receive(request(),actor)).hasMessageContaining("Lý do");}
    @org.junit.jupiter.api.DisplayName("Thiếu lý do bị từ chối")
    @Test void reject_missingReason_rejected(){assertThatThrownBy(()->service.reject(5L,0L," ",actor)).hasMessageContaining("Lý do từ chối");}
    @org.junit.jupiter.api.DisplayName("Sai chi nhánh trả không tìm thấy")
    @Test @com.dienmay.support.TestCase("TC-WAR-21")
    void startRepair_wrongBranch_notFound(){var ticket=new WarrantyTicket();ticket.setBranch(employee.getBranch());when(tickets.findSerialIdentity(5L)).thenReturn(Optional.of(2L));when(tickets.findById(5L)).thenReturn(Optional.of(ticket));doThrow(new ResourceNotFoundException("Không tìm thấy phiếu")).when(access).requireBranch(employee,1L);assertThatThrownBy(()->service.startRepair(5L,0L,actor)).isInstanceOf(ResourceNotFoundException.class);}
}
