package com.dienmay.orders.service;
import com.dienmay.orders.entity.*;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Component;
@Component public class StaffOrderRules {
    public boolean allows(OrderStatus from,OrderStatus to,DeliveryType delivery){
        if(from==null||to==null||delivery==null)return false;
        if(to==OrderStatus.CANCELLED)return from==OrderStatus.PLACED||from==OrderStatus.CONFIRMED||from==OrderStatus.SHIPPING||from==OrderStatus.READY_FOR_PICKUP;
        return switch(from){
            case PLACED -> to==OrderStatus.CONFIRMED;
            case CONFIRMED -> delivery==DeliveryType.DELIVERY?to==OrderStatus.SHIPPING:to==OrderStatus.READY_FOR_PICKUP;
            case SHIPPING -> delivery==DeliveryType.DELIVERY&&to==OrderStatus.COMPLETED;
            case READY_FOR_PICKUP -> delivery==DeliveryType.PICKUP&&to==OrderStatus.COMPLETED;
            default -> false;
        };
    }
    public void check(OrderStatus from,OrderStatus to,DeliveryType delivery){if(!allows(from,to,delivery))throw new BusinessException("Không được chuyển đơn từ "+from+" sang "+to+" với cách nhận "+delivery+".");}
    public String reason(String raw){String reason=OrderRequests.trim(raw);if(reason==null||reason.length()>255)throw new BusinessException("Lý do hủy bắt buộc, tối đa 255 ký tự.");return reason;}
}
