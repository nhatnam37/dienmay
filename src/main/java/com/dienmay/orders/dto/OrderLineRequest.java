package com.dienmay.orders.dto;
import jakarta.validation.constraints.*;
public record OrderLineRequest(@NotNull @Positive Long productId, @Min(1) @Max(20) int quantity) { }
