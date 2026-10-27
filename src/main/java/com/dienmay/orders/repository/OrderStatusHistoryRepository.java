package com.dienmay.orders.repository;
import com.dienmay.orders.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory,Long> {
    List<OrderStatusHistory> findAllByOrderIdOrderByChangedAtAscIdAsc(Long id);
}
