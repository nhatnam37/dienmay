package com.dienmay.warranty.dto;

import com.dienmay.warranty.entity.WarrantyStatus;
import java.time.*;

public record WarrantyView(Long id,Long invoiceItemId,Long serialItemId,String customerName,String customerPhone,LocalDate startDate,LocalDate endDate,WarrantyStatus status,LocalDateTime createdAt) { }
