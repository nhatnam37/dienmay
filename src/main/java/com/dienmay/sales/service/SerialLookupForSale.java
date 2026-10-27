package com.dienmay.sales.service;

import com.dienmay.catalog.entity.ProductStatus;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.serial.*;
import com.dienmay.sales.dto.SaleSerialView;
import com.dienmay.sales.repository.SaleSerialQueries;
import com.dienmay.sales.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class SerialLookupForSale {
    private final SaleSerialQueries serials;
    private final SerialNumberRules rules;
    private final SalesAccess access;
    public SerialLookupForSale(SaleSerialQueries serials,SerialNumberRules rules,SalesAccess access){this.serials=serials;this.rules=rules;this.access=access;}
    public SaleSerialView lookup(String raw,Long branchId,SalesActor actor){
        access.validate(branchId,actor);String number;
        try{number=rules.normalize(raw);}catch(BusinessException exception){return unavailable(raw,exception.getMessage());}
        var found=serials.findForSaleBySerialNo(number);if(found.isEmpty()){return unavailable(number,"Không tìm thấy Serial.");}
        var serial=found.get();var product=serial.getProduct();String reason=null;
        if(!serial.getBranch().getId().equals(branchId)){reason="Serial đang ở chi nhánh khác.";}
        else if(serial.getStatus()==SerialStatus.RESERVED){reason="Serial đang được giữ cho đơn online, chưa thể bán tại quầy.";}
        else if(serial.getStatus()!=SerialStatus.IN_STOCK){
            String state=switch(serial.getStatus()){
                case SOLD -> "đã bán";
                case IN_TRANSFER -> "đang chuyển kho";
                case IN_WARRANTY_REPAIR -> "đang bảo hành";
                case DAMAGED -> "bị hỏng";
                default -> "không sẵn sàng";
            };
            reason="Serial "+state+" ("+serial.getStatus()+"), không thể bán tại quầy.";
        }
        else if(product.getStatus()!=ProductStatus.ACTIVE){reason="Sản phẩm đã ngừng kinh doanh.";}
        // Chỉ gợi ý cho quầy, không khóa/giữ hàng. Checkout phải khóa và quyết định lại.
        return new SaleSerialView(number,product.getId(),product.getSku(),product.getName(),product.getPrice(),serial.getBranch().getId(),serial.getBranch().getName(),serial.getStatus(),reason==null,reason);
    }
    private SaleSerialView unavailable(String serial,String reason){return new SaleSerialView(serial,null,null,null,null,null,null,null,false,reason);}
}
