package com.dienmay.warranty.service;

import com.dienmay.sales.entity.InvoiceItem;
import com.dienmay.sales.service.SalesWarrantyIssuer;
import com.dienmay.warranty.mapper.WarrantyMapper;
import com.dienmay.warranty.repository.WarrantyRepository;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class WarrantyIssuer implements SalesWarrantyIssuer {
    private final WarrantyRepository warranties;
    private final WarrantyMapper mapper;
    public WarrantyIssuer(WarrantyRepository warranties,WarrantyMapper mapper){this.warranties=warranties;this.mapper=mapper;}
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public void issue(InvoiceItem item){
        Integer warrantyMonths=item.getWarrantyMonths();
        // MANDATORY: không tạo bảo hành bằng transaction độc lập, phải rollback cùng thanh toán.
        if(item.getSerialItem()==null||warrantyMonths==null||warrantyMonths<=0||warrantyMonths>60){throw new BusinessException("Dòng hàng không đủ điều kiện tạo bảo hành.");}
        warranties.save(mapper.create(item));
    }
}
