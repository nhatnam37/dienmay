package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.GoodsReceipt;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    boolean existsByCode(String code);
}
