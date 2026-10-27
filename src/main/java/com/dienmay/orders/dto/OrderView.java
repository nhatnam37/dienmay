package com.dienmay.orders.dto;
import com.dienmay.orders.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
// Không chứa Serial hoặc giá vốn trong bất kỳ DTO khách nào.
public record OrderView(Long id,String code,OrderStatus status,String branchName,DeliveryType deliveryType,
        OrderPaymentMethod paymentMethod,String receiverName,String receiverPhone,String deliveryAddress,
        String note,BigDecimal subtotal,BigDecimal totalAmount,LocalDateTime expiresAt,LocalDateTime createdAt,
        String cancelledReason,String paymentStatus,BigDecimal paidAmount,List<Line> items,List<Event> history) {
    public record Line(String name,int quantity,BigDecimal unitPrice,BigDecimal lineTotal) { }
    public record Event(OrderStatus fromStatus,OrderStatus toStatus,String actor,String note,LocalDateTime changedAt) { }
}
