package com.dienmay.warranty.service;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.entity.*;
import com.dienmay.warranty.repository.*;
import com.dienmay.warranty.mapper.WarrantyTicketMapper;
import com.dienmay.warranty.security.WarrantyAccess;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.CheckoutNormalizer;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
@Service @Transactional(readOnly=true)
public class WarrantyQueryService {
    private final WarrantyRepository warranties;private final WarrantyTicketRepository tickets;
    private final WarrantyTicketHistoryRepository histories;private final WarrantyStatusPolicy policy;
    private final SerialNumberRules rules;private final CheckoutNormalizer normalizer;
    private final WarrantyAccess access;private final WarrantyTicketMapper mapper;private final EmployeeRepository employees;
    public WarrantyQueryService(WarrantyRepository warranties,WarrantyTicketRepository tickets,
            WarrantyTicketHistoryRepository histories,WarrantyStatusPolicy policy,SerialNumberRules rules,
            CheckoutNormalizer normalizer,WarrantyAccess access,WarrantyTicketMapper mapper,EmployeeRepository employees){
        this.warranties=warranties;this.tickets=tickets;this.histories=histories;this.policy=policy;this.rules=rules;
        this.normalizer=normalizer;this.access=access;this.mapper=mapper;this.employees=employees;
    }
    public Optional<PublicWarrantyView> publicLookup(String serialNo){
        String serial=normalize(serialNo);if(serial==null)return Optional.empty();
        return warranties.findAllBySerialItemSerialNoOrderByCreatedAtDescIdDesc(serial).stream()
            .filter(w->w.getStatus()==WarrantyStatus.ACTIVE && soldToCustomer(w.getSerialItem().getStatus()))
            .findFirst().map(w->{var result=policy.evaluate(w);
                var open=tickets.findAllByWarrantyIdOrderByReceivedAtAscIdAsc(w.getId()).stream().filter(t->t.getStatus().isOpen()).findFirst();
                return new PublicWarrantyView(w.getSerialItem().getProduct().getName(),w.getStartDate(),w.getEndDate(),
                    result.valid(),result.daysRemaining(),open.map(WarrantyTicket::getStatus).orElse(null));});
    }
    public Optional<PublicTicketView> publicTrack(String ticketCode,String serialNo){
        String serial=normalize(serialNo);if(serial==null || ticketCode==null || ticketCode.isBlank())return Optional.empty();
        return tickets.findByCodeAndSerialItemSerialNo(ticketCode.strip(),serial)
            // So sánh Java chính xác cả mã: collation DB không phân biệt chữ hoa thường.
            .filter(t->t.getCode().equals(ticketCode.strip()) && t.getSerialItem().getSerialNo().equals(serial))
            .map(t->new PublicTicketView(t.getStatus(),t.getReceivedAt(),t.getCompletedAt(),t.getReturnedAt(),
                histories.findAllByTicketIdOrderByChangedAtAscIdAsc(t.getId()).stream()
                    .map(h->new PublicTicketView.Event(h.getToStatus(),h.getChangedAt())).toList()));
    }
    public List<StaffWarrantyView> staffLookup(String serialNo,String phone,SalesActor actor){
        access.validate(actor);
        // Nhân viên bảo hành được đọc xuyên chi nhánh; quyền ghi vẫn giới hạn tại WarrantyTicketService.
        List<Warranty> found;
        if(serialNo!=null&&!serialNo.isBlank())found=warranties.findAllBySerialItemSerialNoOrderByCreatedAtDescIdDesc(rules.normalize(serialNo));
        else {String canonical=normalizer.phone(phone);if(canonical==null||!canonical.matches("0[35789][0-9]{8}"))throw new BusinessException("Số điện thoại không hợp lệ.");found=warranties.findAllByCustomerPhoneOrderByCreatedAtDescIdDesc(canonical);}
        return found.stream().map(w->new StaffWarrantyView(w.getId(),w.getSerialItem().getSerialNo(),w.getSerialItem().getProduct().getName(),
            w.getCustomerName(),w.getCustomerPhone(),w.getInvoiceItem().getInvoice().getId(),w.getInvoiceItem().getInvoice().getCode(),
            w.getInvoiceItem().getInvoice().getBranch().getId(),w.getStartDate(),w.getEndDate(),w.getStatus().name(),
            tickets.findAllByWarrantyIdOrderByReceivedAtAscIdAsc(w.getId()).stream().map(t->new StaffWarrantyView.Ticket(mapper.view(t),
                histories.findAllByTicketIdOrderByChangedAtAscIdAsc(t.getId()).stream().map(h->new StaffWarrantyView.Event(h.getFromStatus(),h.getToStatus(),
                    h.getActorType(),h.getActorId(),h.getActorId()==null?"Hệ thống":employees.findById(h.getActorId()).map(e->e.getFullName()).orElse("Tài khoản không còn tồn tại"),
                    h.getNote(),h.getChangedAt())).toList())).toList())).toList();
    }
    @PreAuthorize("hasRole('CUSTOMER') and principal.customerId == #customerId")
    public List<CustomerWarrantyView> customerWarranties(Long customerId){
        // Không suy quyền sở hữu từ phone: chưa xác minh và khách tự sửa được.
        return warranties.findCustomerWarranties(customerId).stream().map(w->{var result=policy.evaluate(w);
            return new CustomerWarrantyView(w.getId(),w.getSerialItem().getSerialNo(),w.getSerialItem().getProduct().getName(),
                w.getInvoiceItem().getInvoice().getCode(),w.getStartDate(),w.getEndDate(),w.getStatus().name(),result.valid(),result.daysRemaining());}).toList();
    }
    private boolean soldToCustomer(SerialStatus status){return status==SerialStatus.SOLD || status==SerialStatus.IN_WARRANTY_REPAIR;}
    private String normalize(String raw){try{return rules.normalize(raw);}catch(BusinessException invalid){return null;}}
}
