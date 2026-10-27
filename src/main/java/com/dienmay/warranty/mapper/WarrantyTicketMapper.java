package com.dienmay.warranty.mapper;
import com.dienmay.warranty.dto.WarrantyTicketView;
import com.dienmay.warranty.entity.WarrantyTicket;
import org.springframework.stereotype.Component;
@Component
public class WarrantyTicketMapper {
    public WarrantyTicketView view(WarrantyTicket t){
        return new WarrantyTicketView(t.getId(),t.getCode(),t.getWarranty().getId(),t.getSerialItem().getId(),
                t.getSerialItem().getSerialNo(),t.getBranch().getId(),t.getStatus(),t.getIssueDescription(),
                t.isOutOfWarranty(),t.getOverrideReason(),t.getDiagnosis(),t.getResolution(),t.getRejectReason(),
                t.getReceivedAt(),t.getCompletedAt(),t.getReturnedAt(),t.getVersion());
    }
}
