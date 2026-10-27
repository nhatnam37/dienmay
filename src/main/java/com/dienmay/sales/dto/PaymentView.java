package com.dienmay.sales.dto;

public record PaymentView(com.dienmay.sales.entity.PaymentMethod method, java.math.BigDecimal amount, java.math.BigDecimal cashReceived, java.math.BigDecimal changeGiven, String referenceCode, java.time.LocalDateTime paidAt) { }
