package com.dienmay.orders.dto;
import com.dienmay.orders.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
// Danh sách chỉ lấy header, không đọc từng dòng hàng/history/payment theo mỗi đơn.
public record OrderSummary(Long id,String code,String branchName,OrderStatus status,BigDecimal totalAmount,LocalDateTime createdAt,LocalDateTime expiresAt) { }
