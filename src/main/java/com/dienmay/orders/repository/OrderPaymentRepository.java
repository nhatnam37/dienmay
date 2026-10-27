package com.dienmay.orders.repository;
import com.dienmay.orders.entity.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface OrderPaymentRepository extends JpaRepository<OrderPayment,Long> {
    Optional<OrderPayment> findByOrderId(Long id);
}
