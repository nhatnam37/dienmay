package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.GoodsTransferItem;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface GoodsTransferItemRepository extends JpaRepository<GoodsTransferItem, Long> {
    // Khi ghi chỉ đọc ID của quan hệ LAZY: không nạp trạng thái Serial cũ trước SELECT FOR UPDATE.
    List<GoodsTransferItem> findAllByTransferIdOrderByIdAsc(Long transferId);
    @EntityGraph(attributePaths = {"product", "serialItem"})
    @Query("select i from GoodsTransferItem i where i.transfer.id = :id order by i.id")
    List<GoodsTransferItem> findDetailItems(@Param("id") Long id);
}
