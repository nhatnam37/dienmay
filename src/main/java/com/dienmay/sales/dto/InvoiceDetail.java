package com.dienmay.sales.dto;
public record InvoiceDetail(InvoiceView invoice,java.util.Map<Long,java.time.LocalDate> warrantyEnds,java.util.Set<Long> voidedWarranties) {
    public InvoiceDetail(InvoiceView invoice,java.util.Map<Long,java.time.LocalDate> warrantyEnds){this(invoice,warrantyEnds,java.util.Set.of());}
}
