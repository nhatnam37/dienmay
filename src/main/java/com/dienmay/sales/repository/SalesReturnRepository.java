package com.dienmay.sales.repository;
import com.dienmay.sales.entity.SalesReturn;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
public interface SalesReturnRepository extends JpaRepository<SalesReturn,Long> {
    @EntityGraph(attributePaths={"invoice","branch"})
    Page<SalesReturn> findByBranchId(Long branchId,Pageable pageable);
    @EntityGraph(attributePaths={"invoice","branch"})
    java.util.List<SalesReturn> findAllByInvoiceIdOrderByIdAsc(Long invoiceId);
}
