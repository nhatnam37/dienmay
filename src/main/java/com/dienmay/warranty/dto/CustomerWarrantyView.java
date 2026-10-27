package com.dienmay.warranty.dto;
import java.time.LocalDate;
public record CustomerWarrantyView(Long warrantyId,String serialNo,String productName,String invoiceCode,
        LocalDate startDate,LocalDate endDate,String status,boolean valid,long daysRemaining) { }
