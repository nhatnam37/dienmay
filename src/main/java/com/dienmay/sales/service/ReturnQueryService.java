package com.dienmay.sales.service;

import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.security.*;
import com.dienmay.sales.mapper.CheckoutMapper;
import com.dienmay.sales.config.PosProperties;
import com.dienmay.organization.repository.EmployeeRepository;
import com.dienmay.inventory.serial.SerialStatus;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.*;

@Service @Transactional(readOnly=true)
public class ReturnQueryService {
    private final ReturnAccess access;private final InvoiceRepository invoices;private final InvoiceItemRepository items;
    private final SalesReturnRepository returns;private final SalesReturnItemRepository returned;private final PaymentRefundRepository refunds;
    private final PaymentRepository payments;private final CheckoutMapper mapper;private final PosProperties properties;private final Clock clock;private final EmployeeRepository employees;
    public ReturnQueryService(ReturnAccess access,InvoiceRepository invoices,InvoiceItemRepository items,SalesReturnRepository returns,SalesReturnItemRepository returned,PaymentRefundRepository refunds,PaymentRepository payments,CheckoutMapper mapper,PosProperties properties,Clock clock,EmployeeRepository employees){this.access=access;this.invoices=invoices;this.items=items;this.returns=returns;this.returned=returned;this.refunds=refunds;this.payments=payments;this.mapper=mapper;this.properties=properties;this.clock=clock;this.employees=employees;}
    public ReturnScreen screen(Long id,SalesActor actor){
        var employee=access.validate(actor);var invoice=invoices.findById(id).orElseThrow(this::missing);access.branch(employee,invoice.getBranch().getId());
        var sold=items.findAllByInvoiceIdOrderByIdAsc(id);Map<Long,Integer> counts=new HashMap<>();var previous=returned.findForInvoice(id);previous.forEach(r->counts.merge(r.getInvoiceItem().getId(),r.getQuantity(),Integer::sum));
        List<ReturnLineView> lines=sold.stream().map(i->{int count=counts.getOrDefault(i.getId(),0);String reason=null;if(count>=i.getQuantity())reason="Đã trả hết";else if(i.getSerialItem()!=null&&i.getSerialItem().getStatus()!=SerialStatus.SOLD)reason=i.getSerialItem().getStatus()==SerialStatus.IN_WARRANTY_REPAIR?"Đang bảo hành":"Serial không còn ở trạng thái đã bán";return new ReturnLineView(i.getId(),i.getProduct().getName(),i.getSerialItem()==null?null:i.getSerialItem().getSerialNo(),i.getQuantity(),count,i.getQuantity()-count,i.getUnitPrice(),reason==null,reason);}).toList();
        LocalDate today=LocalDate.now(clock),day=invoice.getCreatedAt().toLocalDate();boolean completed=invoice.getStatus()==InvoiceStatus.COMPLETED&&!today.isBefore(day);
        boolean cancel=completed&&previous.isEmpty()&&(!properties.isCancelSameDayOnly()||today.equals(day))&&lines.stream().allMatch(ReturnLineView::returnable);
        boolean partial=completed&&!today.isAfter(day.plusDays(properties.getReturnWindowDays()))&&lines.stream().anyMatch(ReturnLineView::returnable);
        return new ReturnScreen(mapper.view(invoice,sold,payments.findAllByInvoiceIdOrderByIdAsc(id)),lines,cancel,partial,(!cancel&&!partial)?"Hóa đơn không còn đủ điều kiện hủy/trả (trạng thái, thời hạn hoặc Serial).":null);
    }
    public Page<ReturnSummary> list(Long branchId,int page,SalesActor actor){var employee=access.validate(actor);Long branch=employee.getRole().getCode().equals("ADMIN")&&branchId!=null?branchId:employee.getBranch().getId();return returns.findByBranchId(branch,com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,page),10,Sort.by(Sort.Direction.DESC,"createdAt","id"))).map(this::summary);}
    public ReturnDetail detail(Long id,SalesActor actor){var employee=access.validate(actor);var doc=returns.findById(id).orElseThrow(this::missing);access.branch(employee,doc.getBranch().getId());String name=doc.getActorType()==com.dienmay.inventory.context.ActorType.EMPLOYEE?employees.findById(doc.getActorId()).map(e->e.getFullName()).orElse("Nhân viên #"+doc.getActorId()):doc.getActorType().name();return new ReturnDetail(summary(doc),name,returned.findBySalesReturnIdOrderByIdAsc(id).stream().map(i->new ReturnDetail.Item(i.getInvoiceItem().getProduct().getName(),i.getSerialItem()==null?null:i.getSerialItem().getSerialNo(),i.getQuantity(),i.getRefundAmount())).toList(),refunds.findBySalesReturnIdOrderByIdAsc(id).stream().map(p->new ReturnDetail.Refund(p.getPayment().getMethod(),p.getPayment().getReferenceCode(),p.getAmount())).toList());}
    public Map<Long,String> labels(Collection<Long> ids){
        if(ids.isEmpty())return Map.of();Map<Long,Long> total=new HashMap<>();items.quantitiesByInvoice(ids).forEach(row->total.put((Long)row[0],((Number)row[1]).longValue()));Map<Long,String> labels=new HashMap<>();returned.quantitiesByInvoice(ids).forEach(row->{Long id=(Long)row[0];long count=((Number)row[1]).longValue();if(count>0)labels.put(id,count>=total.getOrDefault(id,Long.MAX_VALUE)?"Đã trả hết":"Đã trả một phần");});return labels;
    }
    private ReturnSummary summary(SalesReturn d){return new ReturnSummary(d.getId(),d.getCode(),d.getInvoice().getId(),d.getInvoice().getCode(),d.getBranch().getId(),d.getBranch().getName(),d.getType(),d.getReason(),d.getRefundTotal(),d.getCreatedAt());}
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Không tìm thấy hóa đơn hoặc phiếu trả.");}
}
