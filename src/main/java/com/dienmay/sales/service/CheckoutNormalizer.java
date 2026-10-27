package com.dienmay.sales.service;

import com.dienmay.common.exception.BusinessException;
import com.dienmay.sales.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component
public class CheckoutNormalizer {
    private final ObjectMapper json;
    public CheckoutNormalizer(ObjectMapper json){this.json=json;}
    public String trim(String value){return value==null||value.isBlank()?null:value.strip();}
    public String phone(String value){String phone=trim(value);return phone!=null&&phone.startsWith("+84")?"0"+phone.substring(3):phone;}
    public CheckoutRequest normalize(CheckoutRequest request){
        if(request==null){throw new BusinessException("Thiếu yêu cầu thanh toán.");}
        String token=trim(request.checkoutToken());
        if(token==null||!token.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")){throw new BusinessException("Token thanh toán phải là UUID hợp lệ.");}
        var items=request.items()==null?null:request.items().stream().map(line->line==null?null:new CheckoutLineRequest(line.productId(),trim(line.serialNo())==null?null:trim(line.serialNo()).toUpperCase(Locale.ROOT),line.quantity())).toList();
        var payments=request.payments()==null?null:request.payments().stream().map(payment->payment==null?null:new CheckoutPaymentRequest(payment.method(),payment.amount(),payment.cashReceived(),trim(payment.referenceCode()))).toList();
        return new CheckoutRequest(token.toLowerCase(Locale.ROOT),request.branchId(),trim(request.customerName()),phone(request.customerPhone()),request.discountAmount()==null?BigDecimal.ZERO:request.discountAmount(),trim(request.note()),items,payments);
    }
    private String number(BigDecimal number){return number==null?null:number.stripTrailingZeros().toPlainString();}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException exception){throw new IllegalStateException("Không thể chuẩn hóa yêu cầu thanh toán.",exception);}}
    public String fingerprint(CheckoutRequest request){
        // JSON giữ ranh giới trường (không nối bằng ký tự dễ va chạm); bỏ ảnh hưởng thứ tự các dòng.
        var items=request.items()==null?null:request.items().stream().map(line->encode(line==null?null:Arrays.asList(line.productId(),line.serialNo(),line.quantity()))).sorted().toList();
        var payments=request.payments()==null?null:request.payments().stream().map(payment->encode(payment==null?null:Arrays.asList(payment.method(),number(payment.amount()),number(payment.cashReceived()),payment.referenceCode()))).sorted().toList();
        String content=encode(Arrays.asList(request.branchId(),request.customerName(),request.customerPhone(),number(request.discountAmount()),request.note(),items,payments));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException exception){throw new IllegalStateException(exception);}
    }
}
