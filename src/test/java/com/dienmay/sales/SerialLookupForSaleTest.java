package com.dienmay.sales;

import com.dienmay.catalog.entity.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.sales.repository.SaleSerialQueries;
import com.dienmay.sales.security.*;
import com.dienmay.sales.service.SerialLookupForSale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.dienmay.organization.entity.Branch;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SerialLookupForSaleTest {
    @Mock SaleSerialQueries serials;@Mock SalesAccess access;
    SalesActor actor=new SalesActor(1L,1L,Set.of("ROLE_CASHIER"));
    @Test void lookup_unknownSerial_returnsReason(){when(serials.findForSaleBySerialNo("ABC-123")).thenReturn(Optional.empty());assertThat(new SerialLookupForSale(serials,new SerialNumberRules(),access).lookup(" abc-123 ",1L,actor).reason()).contains("Không tìm thấy");}
    @Test @com.dienmay.support.TestCase("TC-POS-05")
    void lookup_reservedSerial_returnsOnlineReservationReason(){Product p=new Product();p.setStatus(ProductStatus.ACTIVE);Branch b=new Branch();ReflectionTestUtils.setField(b,"id",1L);SerialItem s=new SerialItem();s.setProduct(p);s.setBranch(b);ReflectionTestUtils.setField(s,"status",SerialStatus.RESERVED);when(serials.findForSaleBySerialNo("ABC-123")).thenReturn(Optional.of(s));var view=new SerialLookupForSale(serials,new SerialNumberRules(),access).lookup("ABC-123",1L,actor);assertThat(view.sellable()).isFalse();assertThat(view.reason()).contains("đơn online");}
}
