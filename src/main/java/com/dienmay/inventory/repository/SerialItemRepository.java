package com.dienmay.inventory.repository;

import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface SerialItemRepository extends JpaRepository<SerialItem, Long> {
    boolean existsBySerialNo(String serialNo);
    boolean existsByProductId(Long productId);
    java.util.Optional<SerialItem> findBySerialNo(String serialNo);
    // Một câu IN cho cả lô, không gọi exists trong vòng lặp từng serial.
    java.util.List<SerialItem> findAllBySerialNoIn(java.util.Collection<String> serialNos);
    long countByBranchIdAndProductIdAndStatus(Long branchId, Long productId, SerialStatus status);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerialItem s where s.serialNo in :serialNos order by s.serialNo")
    java.util.List<SerialItem> findAllLockedBySerialNoIn(@Param("serialNos") java.util.Collection<String> serialNos);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerialItem s where s.id = :id")
    java.util.Optional<SerialItem> findByIdForUpdate(@Param("id") Long id);
}
