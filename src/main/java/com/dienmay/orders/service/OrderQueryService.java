package com.dienmay.orders.service;
import com.dienmay.orders.dto.OrderView;
import com.dienmay.orders.entity.*;
import com.dienmay.orders.repository.*;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.util.List;
@Service @Transactional(readOnly=true)
public class OrderQueryService {
    private final OrderRepository orders;private final OrderItemRepository items;private final OrderPaymentRepository payments;private final OrderStatusHistoryRepository history;
    public OrderQueryService(OrderRepository orders,OrderItemRepository items,OrderPaymentRepository payments,OrderStatusHistoryRepository history){this.orders=orders;this.items=items;this.payments=payments;this.history=history;}
    public OrderView get(String code,CustomerActor actor){if(actor==null||actor.customerId()==null)throw missing();return view(orders.findByCodeAndCustomerId(code,actor.customerId()).orElseThrow(this::missing));}
    public org.springframework.data.domain.Page<com.dienmay.orders.dto.OrderSummary> customerPage(CustomerActor actor,OrderStatus status,int page){
        if(actor==null||actor.customerId()==null)throw missing();
        return orders.searchCustomer(actor.customerId(),status,com.dienmay.common.pagination.PageRequestFactory.of(Math.max(page,0),10,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt","id")))
            .map(o->new com.dienmay.orders.dto.OrderSummary(o.getId(),o.getCode(),o.getBranch().getName(),o.getStatus(),o.getTotalAmount(),o.getCreatedAt(),o.getExpiresAt()));
    }
    public List<OrderView> list(CustomerActor actor,int page){return orders.findAllByCustomerIdOrderByCreatedAtDesc(actor.customerId(),com.dienmay.common.pagination.PageRequestFactory.of(Math.max(page,0),10)).stream().map(this::view).toList();}
    public OrderView view(CustomerOrder order){
        var payment=payments.findByOrderId(order.getId());
        return new OrderView(order.getId(),order.getCode(),order.getStatus(),order.getBranch().getName(),order.getDeliveryType(),order.getPaymentMethod(),order.getReceiverName(),order.getReceiverPhone(),order.getDeliveryAddress(),order.getNote(),order.getSubtotal(),order.getTotalAmount(),order.getExpiresAt(),order.getCreatedAt(),order.getCancelledReason(),payment.map(OrderPayment::getStatus).orElse(null),payment.map(OrderPayment::getAmount).orElse(BigDecimal.ZERO),
            items.findAllByOrderIdOrderByIdAsc(order.getId()).stream().map(i->new OrderView.Line(i.getProduct().getName(),i.getQuantity(),i.getUnitPrice(),i.getLineTotal())).toList(),
            history.findAllByOrderIdOrderByChangedAtAscIdAsc(order.getId()).stream().map(h->new OrderView.Event(h.getFromStatus(),h.getToStatus(),switch(h.getActorType()){case SYSTEM->"Hệ thống";case EMPLOYEE->"Nhân viên";case CUSTOMER->"Khách hàng";},h.getNote(),h.getChangedAt())).toList());
    }
    private ResourceNotFoundException missing(){return new ResourceNotFoundException("Không tìm thấy đơn hàng.");}
}
