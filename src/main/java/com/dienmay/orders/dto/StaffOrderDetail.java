package com.dienmay.orders.dto;
import java.util.List;
public record StaffOrderDetail(OrderView order,Long version,Long invoiceId,List<SerialLine> serials) {
    public record SerialLine(Long productId,String productName,String serialNo,com.dienmay.inventory.serial.SerialStatus status,boolean released) { }
}
