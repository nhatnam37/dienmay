package com.dienmay.sales.dto;

import com.dienmay.sales.entity.PaymentMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CheckoutPaymentRequest(
    @NotNull(message="Vui lòng chọn phương thức thanh toán.") PaymentMethod method,
    @NotNull(message="Thiếu số tiền thanh toán.") @DecimalMin(value="0",inclusive=false,message="Số tiền thanh toán phải lớn hơn 0.") @Digits(integer=13,fraction=2,message="Số tiền thanh toán vượt giới hạn.") BigDecimal amount,
    @DecimalMin(value="0",message="Tiền nhận không được âm.") @Digits(integer=13,fraction=2,message="Tiền nhận vượt giới hạn.") BigDecimal cashReceived,
    @Size(max=100,message="Mã tham chiếu tối đa 100 ký tự.") String referenceCode) { }
