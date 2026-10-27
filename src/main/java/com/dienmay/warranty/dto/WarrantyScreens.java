package com.dienmay.warranty.dto;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
import java.time.*;
import java.math.BigDecimal;
import java.util.List;

// DTO riêng cho màn hình; entity không ra tầng view.
public final class WarrantyScreens {
    private WarrantyScreens() { }
    public record BranchChoice(Long id,String name) { }
    public record Lookup(StaffWarrantyView warranty,boolean valid,long daysRemaining,boolean canReceive) { }
    public record Intake(Long warrantyId,String serialNo,String productName,String customerName,
            LocalDate endDate,boolean valid,long daysRemaining,boolean canOverride,boolean canReceive) { }
    public record TicketRow(Long id,String code,String serialNo,String productName,String branchName,
            WarrantyTicketStatus status,LocalDateTime receivedAt) { }
    public record Detail(WarrantyTicketView ticket,String productName,String branchName,String customerName,
            String customerPhone,Long invoiceId,List<StaffWarrantyView.Event> history) { }
    public record CustomerTicket(String code,PublicTicketView tracking) { }
    public record CustomerProfile(CustomerWarrantyView warranty,List<CustomerTicket> tickets) { }
    public record Invoice(String code,String branchName,String customerName,String customerPhone,
            LocalDateTime createdAt,BigDecimal total,List<InvoiceLine> lines) { }
    public record InvoiceLine(String productName,String serialNo,int quantity,BigDecimal price,BigDecimal total) { }
}
