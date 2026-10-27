package com.dienmay.warranty.dto;
import java.time.LocalDateTime;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
public record PublicTicketView(WarrantyTicketStatus status,LocalDateTime receivedAt,
        LocalDateTime completedAt,LocalDateTime returnedAt,java.util.List<Event> timeline) {
    public record Event(WarrantyTicketStatus status,LocalDateTime changedAt) { }
}
