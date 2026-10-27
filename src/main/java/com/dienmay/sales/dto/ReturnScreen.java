package com.dienmay.sales.dto;
public record ReturnScreen(InvoiceView invoice,java.util.List<ReturnLineView> lines,boolean canCancel,boolean canReturn,String message) {  }
