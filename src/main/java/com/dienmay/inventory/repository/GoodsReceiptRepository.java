package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.GoodsReceipt;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    boolean existsByCode(String code);
    boolean existsBySupplierId(Long supplierId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"supplier", "branch", "createdBy"})
    @Query("select r from GoodsReceipt r where (:branchId is null or r.branch.id = :branchId) and (:supplierId is null or r.supplier.id = :supplierId) and (:fromDate is null or r.createdAt >= :fromDate) and (:toDate is null or r.createdAt < :toDate)")
    org.springframework.data.domain.Page<GoodsReceipt> search(@Param("branchId") Long branchId,
        @Param("supplierId") Long supplierId, @Param("fromDate") java.time.LocalDateTime fromDate,
        @Param("toDate") java.time.LocalDateTime toDate, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"supplier", "branch", "createdBy"})
    @Query("select r from GoodsReceipt r where r.id = :id")
    java.util.Optional<GoodsReceipt> findDetail(@Param("id") Long id);
}
