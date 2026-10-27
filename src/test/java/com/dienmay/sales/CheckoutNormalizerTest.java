package com.dienmay.sales;

import com.dienmay.sales.service.CheckoutNormalizer;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CheckoutNormalizerTest {
    CheckoutNormalizer normalizer=new CheckoutNormalizer(new ObjectMapper());
    CheckoutRequest form(String serial,String phone,String money){return new CheckoutRequest("550E8400-E29B-41D4-A716-446655440000",1L," Khách ",phone,new BigDecimal("0.00"),null,List.of(new CheckoutLineRequest(1L,serial,1)),List.of(new CheckoutPaymentRequest(PaymentMethod.CASH,new BigDecimal(money),new BigDecimal("200"),null)));}
    @Test void normalize_serialPhoneAndMoneyFormatting_producesSameFingerprint(){var first=normalizer.normalize(form(" abc-123 ","+84901234567","100.0"));var second=normalizer.normalize(form("ABC-123","0901234567","100.00"));assertThat(first.checkoutToken()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");assertThat(first.customerPhone()).isEqualTo("0901234567");assertThat(normalizer.fingerprint(first)).isEqualTo(normalizer.fingerprint(second));}
    @Test void fingerprint_changedPayment_rejectsTokenReuse(){assertThat(normalizer.fingerprint(normalizer.normalize(form("ABC-123","0901234567","100")))).isNotEqualTo(normalizer.fingerprint(normalizer.normalize(form("ABC-123","0901234567","99"))));}
}
