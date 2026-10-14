package com.dienmay.inventory.repository;

import com.dienmay.inventory.entity.Supplier;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
    java.util.List<Supplier> findAllByActiveTrueOrderByNameAsc();
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.id = :id")
    java.util.Optional<Supplier> findForUpdate(@Param("id") Long id);
    @Query("select s from Supplier s where :keyword is null or lower(s.name) like lower(:keyword) escape '!'")
    org.springframework.data.domain.Page<Supplier> search(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);
}
