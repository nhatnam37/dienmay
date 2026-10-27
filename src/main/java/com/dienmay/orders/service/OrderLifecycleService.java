package com.dienmay.orders.service;

import com.dienmay.orders.entity.*;
import com.dienmay.orders.dto.OrderView;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.orders.config.OrderProperties;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service public class OrderLifecycleService {
    private final OrderRepository orders;private final OrderItemRepository items;private final OrderItemSerialRepository links;private final OrderStatusHistoryRepository history;private final OrderPaymentRepository payments;private final ProductRepository products;private final SerialItemRepository serials;private final SerialStateService states;private final StockService stock;private final OrderQueryService views;private final OrderProperties properties;private final Clock clock;
    public OrderLifecycleService(OrderRepository orders,OrderItemRepository items,OrderItemSerialRepository links,OrderStatusHistoryRepository history,OrderPaymentRepository payments,ProductRepository products,SerialItemRepository serials,SerialStateService states,StockService stock,OrderQueryService views,OrderProperties properties,Clock clock){this.orders=orders;this.items=items;this.links=links;this.history=history;this.payments=payments;this.products=products;this.serials=serials;this.states=states;this.stock=stock;this.views=views;this.properties=properties;this.clock=clock;}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView pay(String code,CustomerActor actor){
        CustomerOrder order=owned(code,actor);LocalDateTime now=LocalDateTime.now(clock);
        if(order.getStatus()==OrderStatus.EXPIRED||expired(order,now))throw new BusinessException("Đơn đã hết hạn.");
        // Nút giả lập có thể gửi lại: không tạo thu lần hai hoặc gia hạn vô hạn.
        if(order.getStatus()==OrderStatus.PLACED&&payments.findByOrderId(order.getId()).filter(p->p.getStatus().equals("PAID")).isPresent())return views.view(order);
        if(order.getStatus()!=OrderStatus.PENDING_PAYMENT||order.getPaymentMethod()!=OrderPaymentMethod.ONLINE)throw new BusinessException("Đơn không chờ thanh toán ONLINE.");
        lockProducts(order);var allocations=lockSerials(order);
        LocalDateTime until=now.plusHours(properties.getPlacedHoldHours());
        for(var link:allocations){SerialItem serial=link.getSerialItem();if(serial.getStatus()!=SerialStatus.RESERVED)throw new BusinessException("Hàng giữ của đơn không còn hợp lệ.");serial.setReservedUntil(until);}
        // CỔNG GIẢ LẬP, không có giao dịch tiền thật. Cổng thật thay bằng webhook xác minh
        // chữ ký, mã giao dịch, số tiền và idempotency; không tin nút thành công từ trình duyệt.
        OrderPayment payment=new OrderPayment();payment.setOrder(order);payment.setAmount(order.getTotalAmount());payment.setMethod("ONLINE");payment.setStatus("PAID");payment.setPaidAt(now);payments.save(payment);
        order.setExpiresAt(until);transition(order,OrderStatus.PLACED,ActorType.CUSTOMER,actor.customerId(),"Thanh toán mô phỏng thành công",now);
        return views.view(order);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public OrderView cancel(String code,String reason,CustomerActor actor){
        CustomerOrder order=owned(code,actor);String note=OrderRequests.trim(reason);if(note!=null&&note.length()>255)throw new BusinessException("Lý do tối đa 255 ký tự.");
        releaseReservation(order,OrderStatus.CANCELLED,ActorType.CUSTOMER,actor.customerId(),note==null?"Khách hủy đơn":note,LocalDateTime.now(clock));return views.view(order);
    }
    // Gọi qua bean khác (job), không self-invocation: mỗi đơn commit/rollback riêng.
    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public boolean expireOne(Long id,LocalDateTime now){
        var found=orders.findForUpdate(id);if(found.isEmpty())return false;var order=found.get();
        // Danh sách ID có thể cũ: khách vừa trả tiền và gia hạn trong khi job chờ khóa.
        // Phải kiểm tra CẢ status VÀ expires_at mới sau SELECT FOR UPDATE.
        if(!reservable(order)||order.getExpiresAt()==null||!order.getExpiresAt().isBefore(now))return false;
        return releaseReservation(order,OrderStatus.EXPIRED,ActorType.SYSTEM,null,"Hết hạn giữ hàng",now);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void releaseByEmployee(CustomerOrder order,Long employeeId,String reason,LocalDateTime now){
        // StaffOrderService đã khóa header, kiểm tra quyền/chi nhánh/version và lý do.
        releaseReservation(order,OrderStatus.CANCELLED,ActorType.EMPLOYEE,employeeId,reason,now);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public List<OrderItemSerial> lockReservations(CustomerOrder order){lockProducts(order);return lockSerials(order);}
    private boolean releaseReservation(CustomerOrder order,OrderStatus target,ActorType actorType,Long actorId,String reason,LocalDateTime now){
        if(order.getStatus()==OrderStatus.CANCELLED||order.getStatus()==OrderStatus.EXPIRED)return false;
        boolean employeeCancellation=actorType==ActorType.EMPLOYEE&&target==OrderStatus.CANCELLED
            &&(order.getStatus()==OrderStatus.CONFIRMED||order.getStatus()==OrderStatus.SHIPPING||order.getStatus()==OrderStatus.READY_FOR_PICKUP);
        if(!reservable(order)&&!employeeCancellation)throw new BusinessException("Đơn không còn cho phép hủy/nhả hàng; đơn hoàn tất không được hoàn tiền.");
        lockProducts(order);var allocations=lockSerials(order);
        for(var link:allocations){var serial=link.getSerialItem();if(serial.getStatus()!=SerialStatus.RESERVED)throw new BusinessException("Serial giữ của đơn không còn ở trạng thái RESERVED.");states.transition(serial,SerialStatus.IN_STOCK,new TransitionContext("ORDER",order.getId(),actorType,actorId,reason,null));link.setReleasedAt(now);}
        for(var item:items.findAllByOrderIdOrderByIdAsc(order.getId()))if(!item.getProduct().isTracksSerial())stock.release(order.getBranch().getId(),item.getProduct().getId(),item.getQuantity(),new StockContext("ORDER",order.getId(),actorType,actorId,reason));
        payments.findByOrderId(order.getId()).filter(p->p.getStatus().equals("PAID")).ifPresent(payment->{
            // HOÀN TIỀN MÔ PHỎNG, cùng transaction nhả hàng và khóa đơn: chỉ đổi PAID một lần,
            // giữ nguyên amount là đúng số đã thu. Cổng thật phải dùng outbox + idempotent refund
            // webhook, không gọi mạng trong transaction khóa kho để giả vờ có atomicity với gateway.
            payment.setStatus("REFUNDED");payment.setRefundedAt(now);payment.setRefundReason(reason);
        });
        // 6C tạo payment hóa đơn từ order_payment đúng một lần khi hoàn tất; báo cáo doanh thu
        // chỉ tính hóa đơn, không cộng sổ tạm thu order_payment thêm lần nữa. COMPLETED bị chặn ở trên.
        order.setCancelledReason(reason);order.setExpiresAt(null);transition(order,target,actorType,actorId,reason,now);return true;
    }
    private void lockProducts(CustomerOrder order){for(Long id:items.findAllByOrderIdOrderByIdAsc(order.getId()).stream().map(i->i.getProduct().getId()).distinct().sorted().toList())products.findForUpdate(id).orElseThrow(()->new BusinessException("Không tìm thấy sản phẩm của đơn."));}
    private List<OrderItemSerial> lockSerials(CustomerOrder order){
        var allocations=links.findAllByOrderItemOrderIdAndReleasedAtIsNullOrderBySerialItemIdAsc(order.getId());
        for(var link:allocations){var current=serials.findByIdForUpdate(link.getSerialItem().getId()).orElseThrow(()->new BusinessException("Không tìm thấy hàng đang giữ."));link.setSerialItem(current);}return allocations;
    }
    private CustomerOrder owned(String code,CustomerActor actor){
        if(actor==null||actor.customerId()==null)throw new ResourceNotFoundException("Không tìm thấy đơn hàng.");
        Long id=orders.findOwnedId(code,actor.customerId()).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        return orders.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy đơn hàng."));
    }
    private boolean reservable(CustomerOrder order){return order.getStatus()==OrderStatus.PENDING_PAYMENT||order.getStatus()==OrderStatus.PLACED;}
    private boolean expired(CustomerOrder order,LocalDateTime now){return order.getExpiresAt()!=null&&!now.isBefore(order.getExpiresAt());}
    private void transition(CustomerOrder order,OrderStatus status,ActorType actorType,Long actorId,String reason,LocalDateTime now){OrderStatusHistory event=new OrderStatusHistory();event.setOrder(order);event.setFromStatus(order.getStatus());event.setToStatus(status);event.setActorType(actorType);event.setActorId(actorId);event.setNote(reason);event.setChangedAt(now);history.save(event);order.setStatus(status);order.setUpdatedAt(now);}
}
