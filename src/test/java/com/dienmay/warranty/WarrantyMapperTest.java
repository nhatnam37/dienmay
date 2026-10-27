package com.dienmay.warranty;

import com.dienmay.sales.entity.Invoice;
import com.dienmay.sales.entity.InvoiceItem;
import com.dienmay.warranty.mapper.WarrantyMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WarrantyMapperTest {
    @Test void create_january31_clampsToLastDayOfFebruary(){
        Invoice invoice=new Invoice();invoice.setCreatedAt(LocalDateTime.of(2026,1,31,23,59));invoice.setCustomerName("Khách");invoice.setCustomerPhone("0901234567");
        InvoiceItem item=new InvoiceItem();item.setInvoice(invoice);item.setWarrantyMonths(1);
        var warranty=new WarrantyMapper().create(item);
        assertThat(warranty.getStartDate()).hasToString("2026-01-31");assertThat(warranty.getEndDate()).hasToString("2026-02-28");assertThat(warranty.getCustomerPhone()).isEqualTo("0901234567");
    }
    @Test void create_leapYear_keepsFebruary29(){
        Invoice invoice=new Invoice();invoice.setCreatedAt(LocalDateTime.of(2024,1,31,12,0));InvoiceItem item=new InvoiceItem();item.setInvoice(invoice);item.setWarrantyMonths(1);
        assertThat(new WarrantyMapper().create(item).getEndDate()).hasToString("2024-02-29");
    }
}
