package com.dienmay.warranty.mapper;

import com.dienmay.warranty.entity.Warranty;
import com.dienmay.warranty.dto.WarrantyView;
import com.dienmay.sales.entity.InvoiceItem;
import org.springframework.stereotype.Component;

@Component
public class WarrantyMapper {
    public Warranty create(InvoiceItem item){
        Integer months=item.getWarrantyMonths();
        if(months==null||months<=0)throw new com.dienmay.common.exception.BusinessException("Chưa đủ snapshot thời hạn bảo hành để cấp hồ sơ.");
        var invoice=item.getInvoice();Warranty warranty=new Warranty();warranty.setInvoiceItem(item);warranty.setSerialItem(item.getSerialItem());
        warranty.setCustomerName(invoice.getCustomerName());warranty.setCustomerPhone(invoice.getCustomerPhone());
        warranty.setStartDate(invoice.getCreatedAt().toLocalDate());warranty.setEndDate(warranty.getStartDate().plusMonths(months));warranty.setCreatedAt(invoice.getCreatedAt());return warranty;
    }
    public WarrantyView view(Warranty warranty){return new WarrantyView(warranty.getId(),warranty.getInvoiceItem().getId(),warranty.getSerialItem().getId(),warranty.getCustomerName(),warranty.getCustomerPhone(),warranty.getStartDate(),warranty.getEndDate(),warranty.getStatus(),warranty.getCreatedAt());}
}
