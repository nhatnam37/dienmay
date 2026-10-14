package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface GoodsTransferRepository extends JpaRepository<GoodsTransfer, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GoodsTransfer t where t.id = :id")
    Optional<GoodsTransfer> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"fromBranch", "toBranch", "createdBy", "shippedBy", "receivedBy"})
    @Query("select t from GoodsTransfer t where t.id = :id")
    Optional<GoodsTransfer> findDetail(@Param("id") Long id);

    @EntityGraph(attributePaths = {"fromBranch", "toBranch", "createdBy"})
    @Query("""
        select t from GoodsTransfer t where (:status is null or t.status = :status)
        and (:branchId is null
          or (:direction = 'OUTGOING' and t.fromBranch.id = :branchId)
          or (:direction = 'INCOMING' and t.toBranch.id = :branchId)
          or (:direction = 'ALL' and (t.fromBranch.id = :branchId or t.toBranch.id = :branchId)))
        """)
    Page<GoodsTransfer> search(@Param("status") TransferStatus status, @Param("branchId") Long branchId,
                              @Param("direction") String direction, Pageable pageable);
}
