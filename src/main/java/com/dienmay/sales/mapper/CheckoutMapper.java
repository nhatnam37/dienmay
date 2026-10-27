package com.dienmay.sales.mapper;

import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CheckoutMapper {
    public InvoiceItemView item(InvoiceItem item){return new InvoiceItemView(item.getId(),item.getProduct().getId(),item.getProduct().getSku(),item.getProduct().getName(),item.getSerialItem()==null?null:item.getSerialItem().getSerialNo(),item.getQuantity(),item.getUnitPrice(),item.getLineTotal());}
    public PaymentView payment(Payment payment){return new PaymentView(payment.getMethod(),payment.getAmount(),payment.getCashReceived(),payment.getChangeGiven(),payment.getReferenceCode(),payment.getPaidAt());}
    public InvoiceView view(Invoice invoice,List<InvoiceItem> items,List<Payment> payments){return new InvoiceView(invoice.getId(),invoice.getCode(),invoice.getCheckoutToken(),invoice.getBranch().getId(),invoice.getBranch().getName(),invoice.getCashier().getId(),invoice.getCashier().getFullName(),invoice.getCustomerName(),invoice.getCustomerPhone(),invoice.getSubtotal(),invoice.getDiscountAmount(),invoice.getTotalAmount(),invoice.getStatus(),invoice.getNote(),invoice.getCreatedAt(),items.stream().map(this::item).toList(),payments.stream().map(this::payment).toList());}
}
