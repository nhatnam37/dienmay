package com.dienmay.storefront.repository;
import com.dienmay.catalog.entity.Product;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
import java.math.BigDecimal;
public interface PublicProductRepository extends Repository<Product,Long> {
    // Fetch chỉ quan hệ to-one, không collection: giữ phân trang DB đúng và không N+1 category/brand.
    @EntityGraph(attributePaths={"category","brand"})
    @Query("""
        select p from Product p where p.status=com.dienmay.catalog.entity.ProductStatus.ACTIVE
        and (:keyword is null or lower(p.name) like :keyword escape '!' or lower(p.sku) like :keyword escape '!')
        and (:category is null or p.category.id=:category) and (:brand is null or p.brand.id=:brand)
        and (:min is null or p.price>=:min) and (:max is null or p.price<=:max)
        """)
    Page<Product> search(@Param("keyword") String keyword,@Param("category") Long category,@Param("brand") Long brand,@Param("min") BigDecimal min,@Param("max") BigDecimal max,Pageable pageable);
    @EntityGraph(attributePaths={"category","brand"})
    @Query("select p from Product p where p.id=:id and p.status=com.dienmay.catalog.entity.ProductStatus.ACTIVE")
    Optional<Product> findActive(@Param("id") Long id);
    @EntityGraph(attributePaths={"category","brand"})
    @Query("select p from Product p where p.id in :ids")
    List<Product> findForCart(@Param("ids") Collection<Long> ids);
}
