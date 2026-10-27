package com.dienmay.sales.repository;
import com.dienmay.sales.entity.PaymentRefund;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund,Long> {
    @Query("select r from PaymentRefund r where r.salesReturn.invoice.id=:id")
    List<PaymentRefund> findForInvoice(@Param("id") Long id);
    @EntityGraph(attributePaths="payment")
    List<PaymentRefund> findBySalesReturnIdOrderByIdAsc(Long id);
}
