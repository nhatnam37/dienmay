package com.dienmay.orders.dto;

import com.dienmay.orders.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

// Không có giá/Serial/customerId do trình duyệt quyết định; service tự tính và chọn hàng.
public record PlaceOrderRequest(
        @NotBlank String placeToken,
        @NotNull @Positive Long branchId,
        @NotNull DeliveryType deliveryType,
        @NotNull OrderPaymentMethod paymentMethod,
        @NotBlank @Size(max=100) String receiverName,
        @NotBlank @Size(max=20) String receiverPhone,
        @Size(max=255) String deliveryAddress,
        @Size(max=255) String note,
        @NotEmpty @Size(max=30) List<@Valid OrderLineRequest> items) { }
