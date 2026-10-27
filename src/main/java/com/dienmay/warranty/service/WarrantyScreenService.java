package com.dienmay.warranty.service;
import com.dienmay.warranty.dto.*;
import com.dienmay.warranty.dto.WarrantyScreens.*;
import com.dienmay.warranty.entity.*;
import com.dienmay.warranty.repository.*;
import com.dienmay.warranty.mapper.WarrantyTicketMapper;
import com.dienmay.warranty.security.WarrantyAccess;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.CheckoutNormalizer;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.*;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import jakarta.persistence.criteria.*;
import java.time.*;
import java.util.*;

@Service @Transactional(readOnly=true)
public class WarrantyScreenService {
    private final WarrantyQueryService queries;private final WarrantyRepository warranties;
    private final WarrantyTicketRepository tickets;private final WarrantyTicketHistoryRepository histories;
    private final WarrantyAccess access;private final WarrantyTicketMapper mapper;private final WarrantyStatusPolicy policy;
    private final SerialNumberRules rules;private final CheckoutNormalizer normalizer;
    private final BranchRepository branches;private final EmployeeRepository employees;
    private final com.dienmay.sales.repository.InvoiceItemRepository invoiceItems;
    public WarrantyScreenService(WarrantyQueryService queries,WarrantyRepository warranties,WarrantyTicketRepository tickets,
            WarrantyTicketHistoryRepository histories,WarrantyAccess access,WarrantyTicketMapper mapper,WarrantyStatusPolicy policy,
            SerialNumberRules rules,CheckoutNormalizer normalizer,BranchRepository branches,EmployeeRepository employees,
            com.dienmay.sales.repository.InvoiceItemRepository invoiceItems){
        this.queries=queries;this.warranties=warranties;this.tickets=tickets;this.histories=histories;this.access=access;
        this.mapper=mapper;this.policy=policy;this.rules=rules;this.normalizer=normalizer;this.branches=branches;
        this.employees=employees;this.invoiceItems=invoiceItems;
    }
    public List<Lookup> lookup(String serialNo,String phone,SalesActor actor){
        Employee employee=access.validate(actor);
        return queries.staffLookup(serialNo,phone,actor).stream().map(view->{
            Warranty warranty=warranties.findById(view.warrantyId()).orElseThrow(this::missing);
            var result=policy.evaluate(warranty);
            return new Lookup(view,result.valid(),result.daysRemaining(),canReceive(warranty,employee,result.valid()));
        }).toList();
    }
    public Intake intake(String serialNo,SalesActor actor){
        Employee employee=access.validate(actor);
        Warranty warranty=warranties.findAllBySerialItemSerialNoOrderByCreatedAtDescIdDesc(rules.normalize(serialNo)).stream()
            .filter(w->w.getStatus()==WarrantyStatus.ACTIVE).findFirst().orElseThrow(this::missing);
        var result=policy.evaluate(warranty);
        return new Intake(warranty.getId(),warranty.getSerialItem().getSerialNo(),warranty.getSerialItem().getProduct().getName(),
            warranty.getCustomerName(),warranty.getEndDate(),result.valid(),result.daysRemaining(),
            !employee.getRole().getCode().equals("WARRANTY_STAFF"),canReceive(warranty,employee,result.valid()));
    }
    private boolean canReceive(Warranty warranty,Employee employee,boolean valid){
        return warranty.getStatus()==WarrantyStatus.ACTIVE && warranty.getSerialItem().getStatus()==SerialStatus.SOLD
            && !tickets.existsOpen(warranty.getSerialItem().getId()) && (valid || !employee.getRole().getCode().equals("WARRANTY_STAFF"));
    }
    public List<BranchChoice> branches(SalesActor actor){Employee employee=access.validate(actor);
        return (employee.getRole().getCode().equals("ADMIN")?branches.findAll(Sort.by("name")):List.of(employee.getBranch()))
            .stream().map(b->new BranchChoice(b.getId(),b.getName())).toList();
    }
    public Page<TicketRow> search(Long branchId,WarrantyTicketStatus status,String code,String serialNo,String phone,
            LocalDate from,LocalDate to,int page,SalesActor actor){
        Employee employee=access.validate(actor);
        boolean admin=employee.getRole().getCode().equals("ADMIN");
        // Không tin bộ lọc branch từ URL: service khóa phạm vi của nhân viên thường.
        if(!admin && branchId!=null && !branchId.equals(employee.getBranch().getId()))throw missing();
        Long scope=admin?branchId:employee.getBranch().getId();
        if(from!=null && to!=null && from.isAfter(to))throw new BusinessException("Ngày bắt đầu phải không sau ngày kết thúc.");
        String serial=blank(serialNo)?null:rules.normalize(serialNo);
        String canonical=blank(phone)?null:normalizer.phone(phone);
        if(canonical!=null&&!canonical.matches("0[35789][0-9]{8}"))throw new BusinessException("Số điện thoại không hợp lệ.");
        String codeFilter=blank(code)?null:code.strip().toUpperCase(Locale.ROOT);
        Specification<WarrantyTicket> spec=(r,q,b)->{
            List<Predicate> all=new ArrayList<>();
            if(scope!=null)all.add(b.equal(r.get("branch").get("id"),scope));
            if(status!=null)all.add(b.equal(r.get("status"),status));
            if(serial!=null)all.add(b.equal(r.get("serialItem").get("serialNo"),serial));
            if(canonical!=null)all.add(b.equal(r.get("warranty").get("customerPhone"),canonical));
            if(codeFilter!=null)all.add(b.like(b.upper(r.get("code")),"%"+escape(codeFilter)+"%",'!'));
            if(from!=null)all.add(b.greaterThanOrEqualTo(r.get("receivedAt"),from.atStartOfDay()));
            if(to!=null)all.add(b.lessThan(r.get("receivedAt"),to.plusDays(1).atStartOfDay()));
            if(!Long.class.equals(q.getResultType()) && !long.class.equals(q.getResultType())){
                // Sắp thứ tự ngay trong SQL trước LIMIT, không sort trang đã lấy trong Java.
                Expression<Integer> closed=b.<Integer>selectCase().when(r.get("status").in(WarrantyTicketStatus.RETURNED,WarrantyTicketStatus.CANCELLED),1).otherwise(0);
                q.orderBy(b.asc(closed),b.asc(r.get("receivedAt")),b.asc(r.get("id")));
            }
            return b.and(all.toArray(Predicate[]::new));
        };
        return tickets.findAll(spec,com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,page),10)).map(t->new TicketRow(t.getId(),t.getCode(),
            t.getSerialItem().getSerialNo(),t.getSerialItem().getProduct().getName(),t.getBranch().getName(),t.getStatus(),t.getReceivedAt()));
    }
    public Detail detail(Long id,SalesActor actor){Employee employee=access.validate(actor);
        WarrantyTicket ticket=tickets.findById(id).orElseThrow(this::missing);access.requireBranch(employee,ticket.getBranch().getId());
        Warranty w=ticket.getWarranty();return new Detail(mapper.view(ticket),ticket.getSerialItem().getProduct().getName(),ticket.getBranch().getName(),
            w.getCustomerName(),w.getCustomerPhone(),w.getInvoiceItem().getInvoice().getId(),history(id));
    }
    public Invoice invoice(Long invoiceId,SalesActor actor){
        access.validate(actor);
        // Chỉ hóa đơn có hồ sơ bảo hành, chỉ đọc xuyên chi nhánh; không cấp quyền thao tác POS.
        var found=warranties.findAllByInvoiceItemInvoiceId(invoiceId);if(found.isEmpty())throw missing();
        var invoice=found.getFirst().getInvoiceItem().getInvoice();
        return new Invoice(invoice.getCode(),invoice.getBranch().getName(),invoice.getCustomerName(),invoice.getCustomerPhone(),invoice.getCreatedAt(),invoice.getTotalAmount(),
            invoiceItems.findAllByInvoiceIdOrderByIdAsc(invoiceId).stream().map(i->new InvoiceLine(i.getProduct().getName(),i.getSerialItem()==null?null:i.getSerialItem().getSerialNo(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList());
    }
    public List<CustomerProfile> customerProfiles(Long customerId){
        // Gọi qua proxy có @PreAuthorize trước khi lấy bất kỳ phiếu nào. Không nhận customerId từ form/URL.
        return queries.customerWarranties(customerId).stream().map(w->new CustomerProfile(w,
            tickets.findAllByWarrantyIdOrderByReceivedAtAscIdAsc(w.warrantyId()).stream().map(t->new CustomerTicket(t.getCode(),
                new PublicTicketView(t.getStatus(),t.getReceivedAt(),t.getCompletedAt(),t.getReturnedAt(),
                    histories.findAllByTicketIdOrderByChangedAtAscIdAsc(t.getId()).stream().map(h->new PublicTicketView.Event(h.getToStatus(),h.getChangedAt())).toList()))).toList())).toList();
    }
    private List<StaffWarrantyView.Event> history(Long id){
        var events=histories.findAllByTicketIdOrderByChangedAtAscIdAsc(id);
        Set<Long> ids=new HashSet<>();events.forEach(h->{if(h.getActorId()!=null)ids.add(h.getActorId());});
        Map<Long,String> names=new HashMap<>();employees.findAllById(ids).forEach(e->names.put(e.getId(),e.getFullName()));
        return events.stream().map(h->new StaffWarrantyView.Event(h.getFromStatus(),h.getToStatus(),h.getActorType(),h.getActorId(),
            h.getActorId()==null?"Hệ thống":names.getOrDefault(h.getActorId(),"Nhân viên #"+h.getActorId()),h.getNote(),h.getChangedAt())).toList();
    }
    private boolean blank(String s){return s==null||s.isBlank();}
    private String escape(String s){return com.dienmay.common.pagination.SearchTerms.escape(s);}
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Không tìm thấy hồ sơ hoặc phiếu bảo hành.");}
}
