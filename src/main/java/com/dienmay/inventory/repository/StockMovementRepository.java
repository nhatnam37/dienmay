package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.StockMovement;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    java.util.List<StockMovement> findAllByBranchIdAndProductIdOrderByCreatedAtAscIdAsc(Long branchId, Long productId);
}
