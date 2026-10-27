package com.dienmay.sales.dto;

import jakarta.validation.constraints.*;

public record CheckoutLineRequest(
    @NotNull(message="Vui lòng chọn sản phẩm.") @Positive(message="Mã sản phẩm không hợp lệ.") Long productId,
    @Size(max=30,message="Serial tối đa 30 ký tự.") String serialNo,
    @NotNull(message="Vui lòng nhập số lượng.") @Positive(message="Số lượng phải lớn hơn 0.") Integer quantity) { }
