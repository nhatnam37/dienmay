package com.dienmay.inventory.repository;

import com.dienmay.inventory.serial.SerialHistory;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface SerialHistoryRepository extends JpaRepository<SerialHistory, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"fromBranch", "toBranch"})
    java.util.List<SerialHistory> findAllBySerialItemIdOrderByChangedAtAscIdAsc(Long serialItemId);
}
