package com.dienmay.sales.repository;

import com.dienmay.sales.entity.InvoiceItem;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem,Long> {
    @EntityGraph(attributePaths={"product","serialItem"})
    List<InvoiceItem> findAllByInvoiceIdOrderByIdAsc(Long invoiceId);
    // Không fetch serialItem trước khóa: tránh trạng thái cũ trong persistence context khi chờ giao dịch khác.
    @Query("select i from InvoiceItem i where i.invoice.id=:id order by i.id")
    List<InvoiceItem> findForReturn(@org.springframework.data.repository.query.Param("id") Long id);
    @Query("select i.invoice.id,sum(i.quantity) from InvoiceItem i where i.invoice.id in :ids group by i.invoice.id")
    List<Object[]> quantitiesByInvoice(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}

