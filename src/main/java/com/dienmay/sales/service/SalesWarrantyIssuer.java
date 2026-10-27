package com.dienmay.sales.service;

import com.dienmay.sales.entity.InvoiceItem;

// Port của sales: warranty cài đặt, checkout không import module warranty.
public interface SalesWarrantyIssuer {
    void issue(InvoiceItem item);
}
