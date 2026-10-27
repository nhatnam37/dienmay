package com.dienmay.orders.service;

import com.dienmay.orders.entity.*;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.security.StaffOrderAccess;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.InvoiceWriter;
import com.dienmay.sales.repository.InvoiceRepository;
import com.dienmay.sales.entity.PaymentMethod;
import com.dienmay.inventory.context.ActorType;
import com.dienmay.inventory.serial.SerialStatus;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service public class StaffOrderService {
    private final OrderRepository orders;private final OrderItemRepository items;private final OrderPaymentRepository payments;private final OrderStatusHistoryRepository history;private final InvoiceRepository invoices;private final InvoiceWriter writer;private final OrderLifecycleService lifecycle;private final StaffOrderAccess access;private final StaffOrderRules rules;private final Clock clock;
    public StaffOrderService(OrderRepository orders,OrderItemRepository items,OrderPaymentRepository payments,OrderStatusHistoryRepository history,InvoiceRepository invoices,InvoiceWriter writer,OrderLifecycleService lifecycle,StaffOrderAccess access,StaffOrderRules rules,Clock clock){this.orders=orders;this.items=items;this.payments=payments;this.history=history;this.invoices=invoices;this.writer=writer;this.lifecycle=lifecycle;this.access=access;this.rules=rules;this.clock=clock;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Long change(Long id,Long expectedVersion,OrderStatus target,String reason,BigDecimal cashReceived,SalesActor actor){
        var employee=access.validate(actor);
        // Cùng header lock với job/khách hủy: đọc lại trạng thái/hạn SAU khi đợi khóa.
        var order=orders.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy đơn hàng."));access.branch(employee,order.getBranch().getId());
        LocalDateTime now=LocalDateTime.now(clock);
        if(target==OrderStatus.CONFIRMED&&(order.getStatus()==OrderStatus.EXPIRED||(order.getStatus()==OrderStatus.PLACED&&order.getExpiresAt()!=null&&!now.isBefore(order.getExpiresAt()))))throw new BusinessException("Đơn đã hết hạn.");
        // @Version chống lost update ở JPA; token version từ form còn chặn thao tác từ tab cũ.
        // Row lock bảo vệ side effect kho và phối hợp job 6B, không thay thế kiểm tra version.
        if(expectedVersion==null||!expectedVersion.equals(order.getVersion()))throw new BusinessException("Đơn đã được cập nhật, tải lại.");
        rules.check(order.getStatus(),target,order.getDeliveryType());
        if(target==OrderStatus.CANCELLED){lifecycle.releaseByEmployee(order,employee.getId(),rules.reason(reason),now);orders.flush();return null;}
        var allocations=lifecycle.lockReservations(order);
        for(var link:allocations)if(link.getSerialItem().getStatus()!=SerialStatus.RESERVED||!link.getSerialItem().getBranch().getId().equals(order.getBranch().getId()))throw new BusinessException("Hàng giữ của đơn không còn hợp lệ.");
        Long invoiceId=null;
        if(target==OrderStatus.CONFIRMED){order.setExpiresAt(null);allocations.forEach(l->l.getSerialItem().setReservedUntil(null));}
        if(target==OrderStatus.COMPLETED){
            if(invoices.existsByOrderId(order.getId()))throw new BusinessException("Đơn đã có hóa đơn, không thể hoàn tất lần hai.");
            List<InvoiceWriter.Line> prepared=new ArrayList<>();
            for(var item:items.findAllByOrderIdOrderByIdAsc(order.getId())){
                // Giá tại lúc đặt là cam kết với khách; không đọc Product.price để lập hóa đơn.
                if(item.getProduct().isTracksSerial()){
                    var selected=allocations.stream().filter(l->l.getOrderItem().getId().equals(item.getId())).toList();
                    if(selected.size()!=item.getQuantity())throw new BusinessException("Thiếu Serial đã giữ cho "+item.getProduct().getName());
                    for(var allocation:selected)prepared.add(new InvoiceWriter.Line(item.getProduct(),allocation.getSerialItem(),1,item.getUnitPrice(),item.getUnitPrice()));
                }else prepared.add(new InvoiceWriter.Line(item.getProduct(),null,item.getQuantity(),item.getUnitPrice(),item.getLineTotal()));
            }
            InvoiceWriter.Tender tender;
            if(order.getPaymentMethod()==OrderPaymentMethod.ONLINE){
                var paid=payments.findByOrderId(order.getId()).filter(p->"PAID".equals(p.getStatus())).orElseThrow(()->new BusinessException("Đơn ONLINE chưa có khoản thu hợp lệ."));
                if(paid.getAmount().compareTo(order.getTotalAmount())!=0||!"ONLINE".equals(paid.getMethod()))throw new BusinessException("Khoản thu không khớp tổng đơn.");
                if(cashReceived!=null)throw new BusinessException("Đơn đã thanh toán ONLINE không nhận thêm tiền mặt.");
                // Chuyển sổ tạm thu sang chứng từ bán đúng một lần; không thu thêm và không cộng
                // order_payment cùng payment khi báo cáo doanh thu. UNIQUE invoice.order_id là chốt DB.
                tender=new InvoiceWriter.Tender(PaymentMethod.ONLINE,paid.getAmount(),null,"ORDER-"+order.getCode(),paid.getPaidAt());
            }else tender=new InvoiceWriter.Tender(PaymentMethod.CASH,order.getTotalAmount(),cashReceived,null,now);
            var invoice=writer.issue(new InvoiceWriter.Draft(order.getBranch(),employee,order.getId(),UUID.randomUUID().toString(),order.getRequestHash(),order.getReceiverName(),order.getReceiverPhone(),order.getNote(),order.getSubtotal(),BigDecimal.ZERO,order.getTotalAmount(),now,prepared,List.of(tender),true));invoiceId=invoice.id();
            // Giữ nguyên liên kết làm lịch sử; hoàn tất đã tiêu thụ giữ chỗ sang hóa đơn.
            allocations.forEach(link->link.setReleasedAt(now));order.setExpiresAt(null);
        }
        OrderStatusHistory event=new OrderStatusHistory();event.setOrder(order);event.setFromStatus(order.getStatus());event.setToStatus(target);event.setActorType(ActorType.EMPLOYEE);event.setActorId(employee.getId());event.setNote(target==OrderStatus.COMPLETED?"Hoàn tất và tạo hóa đơn":"Nhân viên xử lý đơn");event.setChangedAt(now);history.save(event);order.setStatus(target);order.setUpdatedAt(now);orders.flush();return invoiceId;
    }
}
