package com.dienmay.sales.repository;
import com.dienmay.catalog.entity.Product;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface PosProductRepository extends Repository<Product,Long> {
    @Query("select p from Product p where p.tracksSerial=false and p.status=com.dienmay.catalog.entity.ProductStatus.ACTIVE and (lower(p.name) like :keyword escape '!' or lower(p.sku) like :keyword escape '!') order by p.name,p.id")
    List<Product> suggestions(@Param("keyword") String keyword,Pageable pageable);
}
