package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stocktake s where s.id=:id")
    Optional<Stocktake> findForUpdate(@Param("id") Long id);
    @EntityGraph(attributePaths={"branch","createdBy","closedBy"})
    @Query("select s from Stocktake s where s.id=:id")
    Optional<Stocktake> findDetail(@Param("id") Long id);
    @EntityGraph(attributePaths={"branch","createdBy"})
    @Query("select s from Stocktake s where (:branchId is null or s.branch.id=:branchId) and (:status is null or s.status=:status)")
    Page<Stocktake> search(@Param("branchId") Long branchId,@Param("status") StocktakeStatus status,Pageable pageable);
}
