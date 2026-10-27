package com.dienmay.orders.repository;
import com.dienmay.orders.entity.OrderItem;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface OrderItemRepository extends JpaRepository<OrderItem,Long> {
    @EntityGraph(attributePaths={"product"}) List<OrderItem> findAllByOrderIdOrderByIdAsc(Long id);
}
