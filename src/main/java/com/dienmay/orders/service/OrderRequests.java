package com.dienmay.orders.service;
import com.dienmay.orders.dto.*;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.orders.entity.*;
import jakarta.validation.Validator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Component public class OrderRequests {
    private final Validator validator;private final ObjectMapper json;
    public OrderRequests(Validator validator,ObjectMapper json){this.validator=validator;this.json=json;}
    public PlaceOrderRequest normalize(PlaceOrderRequest raw){
        if(raw==null)throw new BusinessException("Thiếu thông tin đặt hàng.");
        String token=trim(raw.placeToken());try{if(token==null||!UUID.fromString(token).toString().equals(token))throw new IllegalArgumentException();}catch(IllegalArgumentException error){throw new BusinessException("Token đặt hàng không hợp lệ.");}
        var r=new PlaceOrderRequest(token,raw.branchId(),raw.deliveryType(),raw.paymentMethod(),trim(raw.receiverName()),CustomerService.phone(raw.receiverPhone()),trim(raw.deliveryAddress()),trim(raw.note()),raw.items()==null?null:new ArrayList<>(raw.items()));
        var errors=validator.validate(r);if(!errors.isEmpty())throw new BusinessException(errors.stream().map(v->v.getMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));
        Set<Long> ids=new HashSet<>();for(var item:r.items()){if(item==null||!ids.add(item.productId()))throw new BusinessException("Mỗi sản phẩm chỉ được xuất hiện một dòng.");}
        if(r.deliveryType()==DeliveryType.DELIVERY&&r.deliveryAddress()==null)throw new BusinessException("Giao tận nơi bắt buộc địa chỉ nhận hàng.");
        if((r.deliveryType()==DeliveryType.PICKUP&&r.paymentMethod()==OrderPaymentMethod.COD)||(r.deliveryType()==DeliveryType.DELIVERY&&r.paymentMethod()==OrderPaymentMethod.PAY_AT_STORE))throw new BusinessException("Phương thức thanh toán không phù hợp cách nhận hàng.");
        return new PlaceOrderRequest(r.placeToken(),r.branchId(),r.deliveryType(),r.paymentMethod(),r.receiverName(),r.receiverPhone(),r.deliveryAddress(),r.note(),r.items().stream().sorted(Comparator.comparing(OrderLineRequest::productId)).toList());
    }
    public String hash(PlaceOrderRequest request){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException("Không thể tạo dấu vân tay đơn.",error);}}
    public static String trim(String value){return value==null||value.isBlank()?null:value.strip();}
}
