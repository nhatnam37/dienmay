package com.dienmay.catalog.repository;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    java.util.Optional<Product> findForUpdate(@Param("id") Long id);

    @Query("select p.tracksSerial from Product p where p.id = :id")
    java.util.Optional<Boolean> findTracksSerialById(@Param("id") Long id);

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);
    long countByCategoryId(Long categoryId);
    long countByBrandId(Long brandId);

    // Chỉ fetch các quan hệ to-one: tránh N+1 khi map tên category/brand,
    // không nhân bản dòng như fetch collection nên vẫn phân trang tại DB.
    @EntityGraph(attributePaths = {"category", "brand"})
    @Query("""
            select p from Product p
            where (:keyword is null or lower(p.name) like lower(:keyword) escape '!'
                or lower(p.sku) like lower(:keyword) escape '!')
              and (:categoryId is null or p.category.id = :categoryId)
              and (:brandId is null or p.brand.id = :brandId)
              and (:status is null or p.status = :status)
            """)
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId,
                         @Param("brandId") Long brandId,
                         @Param("status") ProductStatus status,
                         Pageable pageable);
}
