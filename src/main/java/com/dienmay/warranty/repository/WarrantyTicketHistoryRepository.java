package com.dienmay.warranty.repository;
import com.dienmay.warranty.entity.WarrantyTicketHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WarrantyTicketHistoryRepository extends JpaRepository<WarrantyTicketHistory,Long> {
    List<WarrantyTicketHistory> findAllByTicketIdOrderByChangedAtAscIdAsc(Long ticketId);
}
