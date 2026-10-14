package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.GoodsReceiptItem;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptItemRepository extends JpaRepository<GoodsReceiptItem, Long> {
    boolean existsByProductId(Long productId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "product")
    java.util.List<GoodsReceiptItem> findAllByReceiptIdOrderByIdAsc(Long receiptId);
    default java.util.List<GoodsReceiptItem> findAllByReceiptId(Long receiptId) { return findAllByReceiptIdOrderByIdAsc(receiptId); }
}
