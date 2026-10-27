package com.dienmay.sales.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.inventory.service.StockService;
import com.dienmay.organization.entity.*;
import com.dienmay.sales.dto.InvoiceView;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.mapper.CheckoutMapper;
import com.dienmay.sales.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// Một đường ghi hóa đơn cho POS và đơn online. Caller kiểm tra quyền, khóa hàng,
// chọn nguồn giá; writer dùng đúng snapshot được cấp, không tự tra giá catalog.
@Service
public class InvoiceWriter {
    public record Line(Product product,SerialItem serial,int quantity,BigDecimal price,BigDecimal total) { }
    public record Tender(PaymentMethod method,BigDecimal amount,BigDecimal cashReceived,String reference,LocalDateTime paidAt) { }
    public record Draft(Branch branch,Employee cashier,Long orderId,String token,String requestHash,
            String customerName,String customerPhone,String note,BigDecimal subtotal,BigDecimal discount,
            BigDecimal total,LocalDateTime soldAt,List<Line> lines,List<Tender> tenders,boolean reserved) { }
    private final InvoiceRepository invoices;private final InvoiceItemRepository items;private final PaymentRepository payments;
    private final SerialStateService states;private final StockService stock;private final SalesWarrantyIssuer warranties;private final CheckoutMapper mapper;
    public InvoiceWriter(InvoiceRepository invoices,InvoiceItemRepository items,PaymentRepository payments,SerialStateService states,StockService stock,SalesWarrantyIssuer warranties,CheckoutMapper mapper){this.invoices=invoices;this.items=items;this.payments=payments;this.states=states;this.stock=stock;this.warranties=warranties;this.mapper=mapper;}
    @Transactional(propagation=Propagation.MANDATORY)
    public InvoiceView issue(Draft draft){
        BigDecimal subtotal=money(draft.subtotal(),"Tổng tiền hàng"),discount=money(draft.discount(),"Giảm giá"),total=money(draft.total(),"Tổng hóa đơn");
        if(subtotal.signum()<=0||discount.compareTo(subtotal)>=0||subtotal.subtract(discount).compareTo(total)!=0)throw new BusinessException("Tổng hóa đơn/giảm giá không hợp lệ.");
        BigDecimal sum=BigDecimal.ZERO;
        if(draft.lines()==null||draft.lines().isEmpty())throw new BusinessException("Hóa đơn không có hàng.");
        for(var line:draft.lines()){
            if(line.quantity()<=0||(line.serial()!=null&&line.quantity()!=1)||money(line.price(),"Đơn giá").multiply(BigDecimal.valueOf(line.quantity())).compareTo(money(line.total(),"Thành tiền"))!=0)throw new BusinessException("Dòng hóa đơn không hợp lệ.");
            sum=sum.add(line.total());
        }
        if(sum.compareTo(subtotal)!=0)throw new BusinessException("Tổng dòng hàng không bằng tổng hóa đơn.");
        BigDecimal paid=BigDecimal.ZERO;
        for(var tender:draft.tenders()){
            BigDecimal amount=money(tender.amount(),"Thanh toán");if(amount.signum()<=0)throw new BusinessException("Thanh toán phải lớn hơn 0.");paid=paid.add(amount);
            if(tender.method()==PaymentMethod.ONLINE&&draft.orderId()==null)throw new BusinessException("ONLINE chỉ áp dụng cho đơn website đã thu.");
            if(tender.method()==PaymentMethod.CASH){if(tender.cashReceived()==null||money(tender.cashReceived(),"Tiền nhận").compareTo(amount)<0)throw new BusinessException("Tiền mặt nhận phải lớn hơn hoặc bằng phần thanh toán tiền mặt.");}
            else if(tender.cashReceived()!=null)throw new BusinessException("Thẻ/chuyển khoản/ONLINE không được có tiền khách đưa.");
        }
        if(paid.compareTo(total)!=0)throw new BusinessException("Tổng thanh toán phải bằng tổng hóa đơn: "+total.toPlainString()+".");
        Invoice invoice=new Invoice();invoice.setCode("TMP-"+UUID.randomUUID());invoice.setCheckoutToken(draft.token());invoice.setRequestHash(draft.requestHash());invoice.setOrderId(draft.orderId());invoice.setBranch(draft.branch());invoice.setCashier(draft.cashier());invoice.setCustomerName(draft.customerName());invoice.setCustomerPhone(draft.customerPhone());invoice.setNote(draft.note());invoice.setSubtotal(subtotal);invoice.setDiscountAmount(discount);invoice.setTotalAmount(total);invoice.setCreatedAt(draft.soldAt());invoice=invoices.saveAndFlush(invoice);
        invoice.setCode("HD-"+draft.soldAt().toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%05d",invoice.getId()));
        List<InvoiceItem> savedItems=new ArrayList<>();List<Payment> savedPayments=new ArrayList<>();
        for(var line:draft.lines()){InvoiceItem item=new InvoiceItem();item.setInvoice(invoice);item.setProduct(line.product());item.setSerialItem(line.serial());item.setQuantity(line.quantity());item.setWarrantyMonths(line.product().getWarrantyMonths());item.setUnitPrice(money(line.price(),"Đơn giá"));item.setLineTotal(money(line.total(),"Thành tiền"));savedItems.add(items.save(item));}
        for(var tender:draft.tenders()){Payment payment=new Payment();payment.setInvoice(invoice);payment.setMethod(tender.method());payment.setAmount(money(tender.amount(),"Thanh toán"));payment.setReferenceCode(tender.reference());payment.setPaidAt(tender.paidAt());if(tender.method()==PaymentMethod.CASH){payment.setCashReceived(money(tender.cashReceived(),"Tiền nhận"));payment.setChangeGiven(payment.getCashReceived().subtract(payment.getAmount()));}savedPayments.add(payments.save(payment));}
        for(var line:draft.lines()){
            if(line.serial()!=null){
                SerialStatus expected=draft.reserved()?SerialStatus.RESERVED:SerialStatus.IN_STOCK;
                if(line.serial().getStatus()!=expected)throw new BusinessException("Serial không còn ở trạng thái "+expected+".");
                states.transition(line.serial(),SerialStatus.SOLD,new TransitionContext("INVOICE",invoice.getId(),ActorType.EMPLOYEE,draft.cashier().getId(),draft.note(),null));
            }else{
                var context=new StockContext("INVOICE",invoice.getId(),ActorType.EMPLOYEE,draft.cashier().getId(),draft.note());
                try{if(draft.reserved())stock.consumeReservation(draft.branch().getId(),line.product().getId(),line.quantity(),context);else stock.decrease(draft.branch().getId(),line.product().getId(),line.quantity(),context);}catch(BusinessException error){throw new BusinessException("Không thể bán "+line.product().getSku()+": "+error.getMessage());}
            }
        }
        for(var item:savedItems)if(item.getSerialItem()!=null&&item.getWarrantyMonths()>0)warranties.issue(item);
        invoices.flush();return mapper.view(invoice,savedItems,savedPayments);
    }
    public static BigDecimal money(BigDecimal value,String name){
        if(value==null||value.signum()<0||value.compareTo(new BigDecimal("9999999999999.99"))>0)throw new BusinessException(name+" không hợp lệ hoặc vượt giới hạn DECIMAL(15,2).");
        try{return value.setScale(0,RoundingMode.UNNECESSARY).setScale(2);}catch(ArithmeticException error){throw new BusinessException(name+" phải là số nguyên đồng, không chấp nhận phần lẻ.");}
    }
}
