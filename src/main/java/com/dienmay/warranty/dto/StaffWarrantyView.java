package com.dienmay.warranty.dto;
import java.time.*;
import java.util.List;
import com.dienmay.inventory.context.ActorType;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
public record StaffWarrantyView(Long warrantyId,String serialNo,String productName,String customerName,
        String customerPhone,Long invoiceId,String invoiceCode,Long branchId,LocalDate startDate,
        LocalDate endDate,String warrantyStatus,List<Ticket> tickets) {
    public record Ticket(WarrantyTicketView detail,List<Event> history) { }
    public record Event(WarrantyTicketStatus from,WarrantyTicketStatus to,ActorType actorType,
            Long actorId,String actorName,String note,LocalDateTime changedAt) { }
}
