package com.dienmay.orders.repository;
import com.dienmay.orders.entity.OrderItemSerial;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface OrderItemSerialRepository extends JpaRepository<OrderItemSerial,Long> {
    @EntityGraph(attributePaths={"serialItem","serialItem.product","orderItem"})
    List<OrderItemSerial> findAllByOrderItemOrderIdOrderBySerialItemIdAsc(Long id);
    // Chỉ đọc nội bộ: view khách tuyệt đối không nhận danh sách số Serial.
    List<OrderItemSerial> findAllByOrderItemOrderIdAndReleasedAtIsNullOrderBySerialItemIdAsc(Long id);
}
