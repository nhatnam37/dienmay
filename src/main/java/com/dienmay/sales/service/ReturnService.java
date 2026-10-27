package com.dienmay.sales.service;

import com.dienmay.sales.config.PosProperties;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.security.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.warranty.repository.WarrantyRepository;
import com.dienmay.warranty.entity.WarrantyStatus;
import com.dienmay.common.exception.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReturnService {
    private final InvoiceRepository invoices;private final InvoiceItemRepository items;private final SalesReturnRepository returns;
    private final SalesReturnItemRepository returnItems;private final PaymentRepository payments;private final PaymentRefundRepository refunds;
    private final ProductRepository products;private final SerialItemRepository serials;private final SerialStateService states;
    private final StockService stock;private final WarrantyRepository warranties;private final ReturnAccess access;
    private final PosProperties properties;private final RefundMath math;private final Validator validator;private final Clock clock;
    public ReturnService(InvoiceRepository invoices,InvoiceItemRepository items,SalesReturnRepository returns,SalesReturnItemRepository returnItems,PaymentRepository payments,PaymentRefundRepository refunds,ProductRepository products,SerialItemRepository serials,SerialStateService states,StockService stock,WarrantyRepository warranties,ReturnAccess access,PosProperties properties,RefundMath math,Validator validator,Clock clock){
        this.invoices=invoices;this.items=items;this.returns=returns;this.returnItems=returnItems;this.payments=payments;this.refunds=refunds;this.products=products;this.serials=serials;this.states=states;this.stock=stock;this.warranties=warranties;this.access=access;this.properties=properties;this.math=math;this.validator=validator;this.clock=clock;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long cancelInvoice(Long invoiceId,String reason,SalesActor actor){return perform(invoiceId,null,reason,ReturnType.FULL_CANCEL,actor);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long returnItems(Long invoiceId,ReturnForm form,SalesActor actor){
        if(form==null)throw new BusinessException("Thiếu dữ liệu trả hàng.");
        var violations=validator.validate(form);if(!violations.isEmpty())throw new BusinessException(violations.stream().map(v->v.getMessage()).sorted().collect(Collectors.joining("; ")));
        return perform(invoiceId,form,form.getReason(),ReturnType.PARTIAL_RETURN,actor);
    }
    private Long perform(Long invoiceId,ReturnForm form,String rawReason,ReturnType type,SalesActor actor){
        var employee=access.validate(actor);String reason=rawReason==null?"":rawReason.strip();
        if(reason.isBlank()||reason.length()>255)throw new BusinessException("Lý do bắt buộc, tối đa 255 ký tự.");
        // Khóa HEADER trước khi đọc tổng đã trả; mọi lần trả cùng hóa đơn tuần tự, không thể vượt số lượng.
        // READ_COMMITTED bảo đảm truy vấn sau khi chờ khóa thấy lần trả vừa commit.
        Invoice invoice=invoices.findForUpdate(invoiceId).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy hóa đơn."));access.branch(employee,invoice.getBranch().getId());
        if(invoice.getStatus()!=InvoiceStatus.COMPLETED)throw new BusinessException("Hóa đơn đã hủy, không thể xử lý lại.");
        LocalDate sold=invoice.getCreatedAt().toLocalDate(),today=LocalDate.now(clock);
        if(today.isBefore(sold))throw new BusinessException("Ngày bán không hợp lệ.");
        if(type==ReturnType.FULL_CANCEL&&properties.isCancelSameDayOnly()&&!today.equals(sold))throw new BusinessException("Chỉ được hủy hóa đơn trong ngày bán.");
        if(type==ReturnType.PARTIAL_RETURN&&today.isAfter(sold.plusDays(properties.getReturnWindowDays())))throw new BusinessException("Đã quá thời hạn trả hàng "+properties.getReturnWindowDays()+" ngày.");
        List<InvoiceItem> soldItems=items.findForReturn(invoiceId);List<SalesReturnItem> previous=returnItems.findForInvoice(invoiceId);
        if(type==ReturnType.FULL_CANCEL&&!previous.isEmpty())throw new BusinessException("Hóa đơn đã trả hàng, không được hủy toàn bộ.");
        Map<Long,Integer> already=new HashMap<>();previous.forEach(r->already.merge(r.getInvoiceItem().getId(),r.getQuantity(),Integer::sum));
        Map<Long,Integer> selected=new LinkedHashMap<>();
        if(type==ReturnType.FULL_CANCEL)soldItems.forEach(i->selected.put(i.getId(),i.getQuantity()));
        else{
            if(form.getLines()==null)throw new BusinessException("Vui lòng chọn dòng cần trả.");
            for(var line:form.getLines()){
                if(line==null||line.getInvoiceItemId()==null||line.getQuantity()==null||line.getQuantity()<0)throw new BusinessException("Dòng trả không hợp lệ.");
                if(line.getQuantity()>0&&selected.putIfAbsent(line.getInvoiceItemId(),line.getQuantity())!=null)throw new BusinessException("Dòng hóa đơn bị lặp trong yêu cầu trả.");
            }
        }
        if(type==ReturnType.PARTIAL_RETURN){
            // Mốc số đã trả của form: cùng một form gửi hai lần không thể âm thầm trả tiếp hàng số lượng.
            // Sau khóa header, đọc số đã trả mới nhất rồi từ chối form cũ; không thay thế kiểm tra giới hạn số lượng.
            for(var line:form.getLines())if(line.getQuantity()>0&&(line.getExpectedReturnedQuantity()==null||!line.getExpectedReturnedQuantity().equals(already.getOrDefault(line.getInvoiceItemId(),0))))throw new BusinessException("Dữ liệu trả hàng đã thay đổi, vui lòng tải lại form để kiểm tra số còn lại.");
        }
        if(selected.isEmpty())throw new BusinessException("Vui lòng chọn ít nhất một sản phẩm cần trả.");
        Map<Long,InvoiceItem> byId=soldItems.stream().collect(Collectors.toMap(InvoiceItem::getId,i->i));
        for(var entry:selected.entrySet()){
            InvoiceItem item=byId.get(entry.getKey());if(item==null)throw new BusinessException("Dòng trả không thuộc hóa đơn.");
            if(entry.getValue()>item.getQuantity()-already.getOrDefault(item.getId(),0))throw new BusinessException("Trả vượt số lượng còn lại của dòng "+item.getId()+".");
            if(item.getSerialItem()!=null&&(entry.getValue()!=1||item.isReturned()))throw new BusinessException("Serial của dòng "+item.getId()+" đã trả hoặc số lượng không bằng 1.");
        }
        List<InvoiceItem> selectedItems=selected.keySet().stream().sorted().map(byId::get).toList();
        // Cùng thứ tự với checkout/chuyển kho: product ID tăng dần rồi Serial ID tăng dần.
        for(Long id:selectedItems.stream().map(i->i.getProduct().getId()).distinct().sorted().toList())products.findForUpdate(id).orElseThrow(()->new BusinessException("Sản phẩm không tồn tại."));
        Map<Long,SerialItem> locked=new HashMap<>();
        for(Long id:selectedItems.stream().filter(i->i.getSerialItem()!=null).map(i->i.getSerialItem().getId()).sorted().toList()){
            SerialItem serial=serials.findByIdForUpdate(id).orElseThrow(()->new BusinessException("Không tìm thấy Serial."));
            if(serial.getStatus()==SerialStatus.IN_WARRANTY_REPAIR)throw new BusinessException("Serial "+serial.getSerialNo()+" đang bảo hành, phải hoàn tất bảo hành trước khi trả.");
            if(serial.getStatus()!=SerialStatus.SOLD)throw new BusinessException("Serial "+serial.getSerialNo()+" không ở trạng thái đã bán (SOLD).");locked.put(id,serial);
        }
        BigDecimal previousGross=previous.stream().map(r->r.getInvoiceItem().getUnitPrice().multiply(BigDecimal.valueOf(r.getQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<BigDecimal> gross=selectedItems.stream().map(i->i.getUnitPrice().multiply(BigDecimal.valueOf(selected.get(i.getId())))).toList();
        BigDecimal oldRefund=previous.stream().map(SalesReturnItem::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal total=math.cumulativeTarget(invoice.getTotalAmount(),invoice.getSubtotal(),previousGross.add(gross.stream().reduce(BigDecimal.ZERO,BigDecimal::add))).subtract(oldRefund);
        if(total.signum()<0)throw new BusinessException("Dữ liệu hoàn tiền không nhất quán.");
        LocalDateTime now=LocalDateTime.now(clock);SalesReturn document=new SalesReturn();document.setInvoice(invoice);document.setBranch(invoice.getBranch());document.setType(type);document.setReason(reason);document.setRefundTotal(total);document.setActorType(ActorType.EMPLOYEE);document.setActorId(employee.getId());document.setCreatedAt(now);document.setCode("TMP-"+UUID.randomUUID());document=returns.saveAndFlush(document);
        document.setCode("TH-"+today.format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%05d",document.getId()));
        List<BigDecimal> amounts=math.allocate(total,gross,0);
        for(int index=0;index<selectedItems.size();index++){
            InvoiceItem item=selectedItems.get(index);int quantity=selected.get(item.getId());SalesReturnItem returned=new SalesReturnItem();returned.setSalesReturn(document);returned.setInvoiceItem(item);returned.setSerialItem(item.getSerialItem());returned.setQuantity(quantity);returned.setRefundAmount(amounts.get(index));returnItems.save(returned);
            if(item.getSerialItem()!=null){
                SerialItem serial=locked.get(item.getSerialItem().getId());states.transition(serial,SerialStatus.IN_STOCK,new TransitionContext("SALES_RETURN",document.getId(),ActorType.EMPLOYEE,employee.getId(),reason,invoice.getBranch().getId()));
                item.setReturned(true); // Chỉ metadata quyền sở hữu; không sửa Serial, giá, số lượng hoặc tiền trên dòng gốc.
                warranties.findByInvoiceItemId(item.getId()).ifPresent(w->{w.setStatus(WarrantyStatus.VOIDED);warranties.save(w);});
            }else stock.increase(invoice.getBranch().getId(),item.getProduct().getId(),quantity,new StockContext("SALES_RETURN",document.getId(),ActorType.EMPLOYEE,employee.getId(),reason));
        }
        refundPayments(invoice,document,total,now);
        if(type==ReturnType.FULL_CANCEL)invoice.setStatus(InvoiceStatus.CANCELLED); // Ngoại lệ trạng thái được yêu cầu; mọi giá trị tài chính gốc bất biến.
        returns.flush();return document.getId();
    }
    private void refundPayments(Invoice invoice,SalesReturn document,BigDecimal total,LocalDateTime now){
        List<Payment> original=payments.findAllByInvoiceIdOrderByIdAsc(invoice.getId());Map<Long,BigDecimal> used=new HashMap<>();refunds.findForInvoice(invoice.getId()).forEach(r->used.merge(r.getPayment().getId(),r.getAmount(),BigDecimal::add));
        List<BigDecimal> remaining=original.stream().map(p->p.getAmount().subtract(used.getOrDefault(p.getId(),BigDecimal.ZERO))).toList();
        if(remaining.stream().anyMatch(a->a.signum()<0)||remaining.stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(total)<0)throw new BusinessException("Số tiền hoàn vượt số đã thu còn lại.");
        // Phần dư lớn nhất, nguyên đồng: floor tỷ lệ số dư của từng payment, chia từng đồng thiếu cho dư lớn nhất.
        // Do số dư nguyên và tổng số dư >= tổng hoàn, mỗi phần được chia không vượt số dư của payment.
        // Không dùng cash_received: đó bao gồm cả tiền đã thối cho khách. Lần cuối hoàn đúng mọi số dư.
        if(total.signum()==0)return;
        List<BigDecimal> allocated=math.allocate(total,remaining,0);
        for(int i=0;i<original.size();i++)if(allocated.get(i).signum()>0){PaymentRefund refund=new PaymentRefund();refund.setSalesReturn(document);refund.setPayment(original.get(i));refund.setAmount(allocated.get(i));refund.setCreatedAt(now);refunds.save(refund);}
    }
}
