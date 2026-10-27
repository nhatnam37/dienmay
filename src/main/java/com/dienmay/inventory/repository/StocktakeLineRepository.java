package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.StocktakeLine;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, Long> {
    @EntityGraph(attributePaths="product")
    List<StocktakeLine> findAllByStocktakeIdOrderByIdAsc(Long stocktakeId);
    @Modifying
    @Query("delete from StocktakeLine line where line.stocktake.id=:id")
    void deleteForStocktake(@Param("id") Long id);
}
