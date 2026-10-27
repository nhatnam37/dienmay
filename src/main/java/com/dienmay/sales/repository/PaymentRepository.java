package com.dienmay.sales.repository;

import com.dienmay.sales.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment,Long> {
    List<Payment> findAllByInvoiceIdOrderByIdAsc(Long invoiceId);
}
