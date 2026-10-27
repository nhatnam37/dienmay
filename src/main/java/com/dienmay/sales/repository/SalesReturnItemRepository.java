package com.dienmay.sales.repository;
import com.dienmay.sales.entity.SalesReturnItem;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem,Long> {
    @Query("select r from SalesReturnItem r where r.salesReturn.invoice.id=:id")
    List<SalesReturnItem> findForInvoice(@Param("id") Long id);
    @EntityGraph(attributePaths={"invoiceItem","invoiceItem.product","serialItem"})
    List<SalesReturnItem> findBySalesReturnIdOrderByIdAsc(Long id);
    @Query("select r.salesReturn.invoice.id,sum(r.quantity) from SalesReturnItem r where r.salesReturn.invoice.id in :ids group by r.salesReturn.invoice.id")
    List<Object[]> quantitiesByInvoice(@Param("ids") Collection<Long> ids);
}
