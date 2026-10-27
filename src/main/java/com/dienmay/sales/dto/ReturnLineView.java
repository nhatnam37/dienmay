package com.dienmay.sales.dto;
public record ReturnLineView(Long invoiceItemId,String productName,String serialNo,int soldQuantity,int returnedQuantity,int remaining,java.math.BigDecimal unitPrice,boolean returnable,String blockedReason) {  }
