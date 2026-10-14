package com.dienmay.inventory.repository;

import com.dienmay.inventory.serial.SerialItem;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface SerialItemRepository extends JpaRepository<SerialItem, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"product", "branch", "receipt"})
    java.util.Optional<SerialItem> findLookupBySerialNo(String serialNo);
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
    @Query("select new com.dienmay.inventory.dto.ReceiptSerialView(s.product.id, s.serialNo, s.status) from SerialItem s where s.receipt.id = :receiptId order by s.id")
    java.util.List<com.dienmay.inventory.dto.ReceiptSerialView> findReceiptSerials(@Param("receiptId") Long receiptId);

    // Một GROUP BY trả tất cả trạng thái cho tất cả sản phẩm/chi nhánh trong phạm vi.
    @Query("""
        select s.branch.id as branchId, s.branch.name as branchName, s.product.id as productId,
               s.product.sku as sku, s.product.name as productName, s.product.category.name as categoryName,
               s.status as status, count(s) as quantity
        from SerialItem s
        where (:branchId is null or s.branch.id = :branchId)
          and (:categoryId is null or s.product.category.id = :categoryId)
          and (:keyword is null or lower(s.product.name) like lower(:keyword) escape '!' or lower(s.product.sku) like lower(:keyword) escape '!')
        group by s.branch.id, s.branch.name, s.product.id, s.product.sku, s.product.name, s.product.category.name, s.status
        order by s.branch.name, s.product.name
        """)
    java.util.List<SerialStockProjection> summarize(@Param("branchId") Long branchId,
        @Param("categoryId") Long categoryId, @Param("keyword") String keyword);
}
