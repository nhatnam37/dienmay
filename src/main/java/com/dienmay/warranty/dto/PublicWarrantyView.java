package com.dienmay.warranty.dto;
import java.time.LocalDate;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
// Cấu trúc riêng: không chứa định danh khách, chi nhánh, giá vốn hay Serial nội bộ.
public record PublicWarrantyView(String productName,LocalDate startDate,LocalDate endDate,
        boolean valid,long daysRemaining,WarrantyTicketStatus repairStatus) { }
