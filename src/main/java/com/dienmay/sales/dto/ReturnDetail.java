package com.dienmay.sales.dto;
public record ReturnDetail(ReturnSummary summary,String actorName,java.util.List<Item> items,java.util.List<Refund> refunds) {  public record Item(String productName,String serialNo,int quantity,java.math.BigDecimal refundAmount){} public record Refund(com.dienmay.sales.entity.PaymentMethod method,String referenceCode,java.math.BigDecimal amount){}  }
