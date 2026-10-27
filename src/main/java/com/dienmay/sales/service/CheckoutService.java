package com.dienmay.sales.service;

import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.repository.SerialItemRepository;
import com.dienmay.inventory.serial.*;
import com.dienmay.sales.config.PosProperties;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.*;
import com.dienmay.sales.mapper.CheckoutMapper;
import com.dienmay.sales.repository.*;
import com.dienmay.sales.security.*;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CheckoutService {
    private final InvoiceWriter writer;
    private final InvoiceRepository invoices;private final InvoiceItemRepository items;private final PaymentRepository payments;
    private final CheckoutGuard guard;private final ProductRepository products;private final SaleSerialQueries saleSerials;
    private final SerialItemRepository serials;
    private final SalesAccess access;private final CheckoutNormalizer normalizer;
    private final CheckoutMapper mapper;private final SerialNumberRules rules;private final Validator validator;private final PosProperties properties;private final Clock clock;
    public CheckoutService(InvoiceRepository invoices,InvoiceItemRepository items,PaymentRepository payments,CheckoutGuard guard,
            ProductRepository products,SaleSerialQueries saleSerials,SerialItemRepository serials,SalesAccess access,CheckoutNormalizer normalizer,CheckoutMapper mapper,SerialNumberRules rules,Validator validator,PosProperties properties,Clock clock,InvoiceWriter writer){
        this.invoices=invoices;this.items=items;this.payments=payments;this.guard=guard;this.products=products;this.saleSerials=saleSerials;
        this.serials=serials;this.access=access;this.normalizer=normalizer;
        this.mapper=mapper;this.rules=rules;this.validator=validator;this.properties=properties;this.clock=clock;this.writer=writer;
    }
    

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public InvoiceView checkout(CheckoutRequest raw,SalesActor actor){
        CheckoutRequest request=normalizer.normalize(raw);
        var existing=invoices.findByCheckoutToken(request.checkoutToken());
        var scope=access.validate(request.branchId(),actor);String hash=normalizer.fingerprint(request);
        // Retry cùng token trả đúng snapshot hóa đơn, không giảm tồn/tạo payment/bảo hành lần nữa.
        // Vẫn kiểm tra quyền trước khi trả để token không trở thành cách đọc hóa đơn người khác.
        if(existing.isPresent()){return replay(existing.get(),request,actor,hash);}
        guard.acquire(request.checkoutToken());
        // READ_COMMITTED để thấy commit vừa hoàn tất khi chờ token; không khóa gap hóa đơn chưa có.
        // FOR UPDATE trên token chưa tồn tại ở RR có thể gây deadlock khi hai token cùng gap rồi INSERT.
        existing=invoices.findByCheckoutToken(request.checkoutToken());
        if(existing.isPresent()){return replay(existing.get(),request,actor,hash);}
        var violations=validator.validate(request);
        if(!violations.isEmpty()){throw new BusinessException(violations.stream().map(v->v.getMessage()).sorted().collect(Collectors.joining("; ")));}
        boolean hasSerial=request.items().stream().anyMatch(line->line.serialNo()!=null);
        if(request.customerPhone()!=null&&!request.customerPhone().matches("0[35789][0-9]{8}")){throw new BusinessException("Số điện thoại di động Việt Nam không hợp lệ.");}
        List<Long> productIds=request.items().stream().map(CheckoutLineRequest::productId).distinct().sorted().toList();
        Map<Long,Product> productMap=new HashMap<>();
        // Khóa product trước serial/stock giống nhập/chuyển kho để giá và cấu hình không đổi giữa lúc bán.
        for(Long id:productIds){productMap.put(id,products.findForUpdate(id).orElseThrow(()->new BusinessException("Không tìm thấy sản phẩm: "+id)));}
        Set<String> numbers=new LinkedHashSet<>();
        for(var line:request.items()){
            Product product=productMap.get(line.productId());if(product.getStatus()!=ProductStatus.ACTIVE){throw new BusinessException("Sản phẩm đã ngừng kinh doanh: "+product.getSku());}
            if(product.isTracksSerial()){
                hasSerial=true;if(line.quantity()!=1){throw new BusinessException("Mỗi dòng Serial phải có số lượng bằng 1: "+product.getSku());}
                var result=rules.validate(line.serialNo(),product.getSerialType());if(!result.valid()){throw new BusinessException(product.getSku()+": "+result.reason());}
                if(!numbers.add(result.normalized())){throw new BusinessException("Serial bị trùng trong giỏ: "+result.normalized());}
            }else if(line.serialNo()!=null){throw new BusinessException("Hàng không Serial không được nhập Serial: "+product.getSku());}
        }
        if(hasSerial&&request.customerPhone()==null){throw new BusinessException("Bắt buộc số điện thoại khách khi mua hàng Serial.");}
        Map<String,SaleSerialIdentity> identities=numbers.isEmpty()?Map.of():saleSerials.findIdentities(numbers).stream().collect(Collectors.toMap(SaleSerialIdentity::serialNo,s->s));
        for(String number:numbers){if(!identities.containsKey(number)){throw new BusinessException("Không tìm thấy Serial: "+number);}}
        Map<String,SerialItem> locked=new HashMap<>();
        // Cùng ID nhỏ trước: hai thu ngân có giỏ A,B và B,A không giữ khóa chéo tạo vòng chờ.
        // Discovery ở trên chỉ là scalar; sau SELECT FOR UPDATE mới đọc trạng thái/chi nhánh thực tế.
        for(var identity:identities.values().stream().sorted(Comparator.comparing(SaleSerialIdentity::id)).toList()){
            SerialItem serial=serials.findByIdForUpdate(identity.id()).orElseThrow(()->new BusinessException("Serial không còn tồn tại: "+identity.serialNo()));locked.put(serial.getSerialNo(),serial);
        }
        List<InvoiceWriter.Line> prepared=new ArrayList<>();BigDecimal subtotal=BigDecimal.ZERO;
        for(var line:request.items()){
            Product product=productMap.get(line.productId());SerialItem serial=line.serialNo()==null?null:locked.get(line.serialNo());
            if(product.isTracksSerial()){
                if(!serial.getProduct().getId().equals(product.getId())){throw new BusinessException("Serial "+serial.getSerialNo()+" không thuộc sản phẩm đã chọn.");}
                if(!serial.getBranch().getId().equals(request.branchId())){throw new BusinessException("Serial "+serial.getSerialNo()+" đang ở chi nhánh khác.");}
                if(serial.getStatus()!=SerialStatus.IN_STOCK){throw new BusinessException("Serial "+serial.getSerialNo()+" không IN_STOCK: "+serial.getStatus()+".");}
            }
            BigDecimal price=money(product.getPrice(),"Giá sản phẩm");BigDecimal lineTotal=money(price.multiply(BigDecimal.valueOf(line.quantity())),"Thành tiền dòng hàng");
            subtotal=money(subtotal.add(lineTotal),"Tổng tiền hàng");prepared.add(new InvoiceWriter.Line(product,serial,line.quantity(),price,lineTotal));
        }
        BigDecimal discount=money(request.discountAmount(),"Giảm giá");
        if(discount.compareTo(subtotal)>=0){throw new BusinessException("Giảm giá phải nhỏ hơn tổng tiền hàng; không được giảm 100% hoặc tạo hóa đơn tổng tiền bằng 0.");}
        if(!scope.privileged()&&discount.multiply(new BigDecimal("100")).compareTo(subtotal.multiply(properties.getMaxCashierDiscountPercent()))>0){throw new BusinessException("Thu ngân chỉ được giảm tối đa "+properties.getMaxCashierDiscountPercent().toPlainString()+"%.");}
        BigDecimal total=subtotal.subtract(discount);
        // POS không được tự khai ONLINE đã thu. Kiểm tra tiền/tiền thối nằm ở writer chung.
        for(var payment:request.payments())if(payment.method()==PaymentMethod.ONLINE)throw new BusinessException("Thanh toán ONLINE chỉ áp dụng cho đơn website.");
        LocalDateTime soldAt=LocalDateTime.now(clock);
        return writer.issue(new InvoiceWriter.Draft(scope.employee().getBranch(),scope.employee(),null,
            request.checkoutToken(),hash,request.customerName(),request.customerPhone(),request.note(),
            subtotal,discount,total,soldAt,prepared,request.payments().stream().map(p->
                new InvoiceWriter.Tender(p.method(),p.amount(),p.cashReceived(),p.referenceCode(),soldAt)).toList(),false));
    }

    private InvoiceView replay(Invoice invoice,CheckoutRequest request,SalesActor actor,String hash){
        if(!invoice.getCashier().getId().equals(actor.employeeId())||!invoice.getBranch().getId().equals(request.branchId())){throw new AccessDeniedException("Token thanh toán không thuộc người lập/chi nhánh hiện tại.");}
        if(!invoice.getRequestHash().equals(hash)){throw new BusinessException("Token đã dùng với nội dung giỏ hàng hoặc thanh toán khác. Hãy tạo token mới.");}
        return mapper.view(invoice,items.findAllByInvoiceIdOrderByIdAsc(invoice.getId()),payments.findAllByInvoiceIdOrderByIdAsc(invoice.getId()));
    }
    private BigDecimal money(BigDecimal value,String name){return InvoiceWriter.money(value,name);}
}
