package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.StockQuantity;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface StockQuantityRepository extends JpaRepository<StockQuantity, Long> {
    java.util.Optional<StockQuantity> findByBranchIdAndProductId(Long branchId, Long productId);
    boolean existsByProductIdAndQuantityGreaterThan(Long productId, int quantity);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockQuantity s where s.branch.id = :branchId and s.product.id = :productId")
    java.util.Optional<StockQuantity> findLocked(@Param("branchId") Long branchId, @Param("productId") Long productId);
}
