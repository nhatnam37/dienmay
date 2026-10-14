package com.dienmay.catalog.repository;

import com.dienmay.catalog.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    // So sánh bằng collation as_ci của DB: không đổi lower/upper trong Java.
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
