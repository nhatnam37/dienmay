package com.dienmay.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutRequest(
    @NotBlank(message="Thiếu token thanh toán.") String checkoutToken,
    @NotNull(message="Thiếu chi nhánh bán.") @Positive(message="Chi nhánh không hợp lệ.") Long branchId,
    @Size(max=100,message="Tên khách tối đa 100 ký tự.") String customerName,
    @Size(max=20,message="Số điện thoại quá dài.") String customerPhone,
    @NotNull @DecimalMin(value="0",message="Giảm giá không được âm.") @Digits(integer=13,fraction=2,message="Giảm giá vượt giới hạn.") BigDecimal discountAmount,
    String note,
    @NotEmpty(message="Giỏ hàng không được rỗng.") List<@NotNull(message="Dòng giỏ hàng không hợp lệ.") @Valid CheckoutLineRequest> items,
    @NotEmpty(message="Hóa đơn phải có ít nhất một dòng thanh toán.") List<@NotNull(message="Dòng thanh toán không hợp lệ.") @Valid CheckoutPaymentRequest> payments) { }
