package com.dienmay.warranty.dto;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
import java.time.LocalDateTime;
public record WarrantyTicketView(Long id,String code,Long warrantyId,Long serialItemId,String serialNo,
        Long branchId,WarrantyTicketStatus status,String issueDescription,boolean outOfWarranty,String overrideReason,
        String diagnosis,String resolution,String rejectReason,LocalDateTime receivedAt,LocalDateTime completedAt,
        LocalDateTime returnedAt,Long version) { }
