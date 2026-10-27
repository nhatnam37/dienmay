package com.dienmay.warranty.repository;

import com.dienmay.warranty.entity.Warranty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WarrantyRepository extends JpaRepository<Warranty,Long> {
    Optional<Warranty> findBySerialItemIdAndStatus(Long serialItemId, com.dienmay.warranty.entity.WarrantyStatus status);
    java.util.List<Warranty> findAllBySerialItemSerialNoOrderByCreatedAtDescIdDesc(String serialNo);
    java.util.List<Warranty> findAllByCustomerPhoneOrderByCreatedAtDescIdDesc(String phone);
    @org.springframework.data.jpa.repository.Query("select w.serialItem.id from Warranty w where w.id=:id")
    Optional<Long> findSerialIdentity(@org.springframework.data.repository.query.Param("id") Long id);
    // Quyền sở hữu dựa trên đơn online; số điện thoại chưa xác minh, khách tự sửa được.
    @org.springframework.data.jpa.repository.Query("select w from Warranty w where w.invoiceItem.invoice.orderId in (select o.id from CustomerOrder o where o.customer.id=:customerId) order by w.createdAt desc,w.id desc")
    java.util.List<Warranty> findCustomerWarranties(@org.springframework.data.repository.query.Param("customerId") Long customerId);
    Optional<Warranty> findByInvoiceItemId(Long invoiceItemId);
    java.util.List<Warranty> findAllByInvoiceItemInvoiceId(Long invoiceId);
}

