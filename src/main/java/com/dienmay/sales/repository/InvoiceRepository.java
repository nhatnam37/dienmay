package com.dienmay.sales.repository;

import com.dienmay.sales.entity.Invoice;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice,Long> {
    boolean existsByOrderId(Long orderId);
    Optional<Invoice> findByOrderId(Long orderId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id=:id")
    Optional<Invoice> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @EntityGraph(attributePaths={"branch","cashier"})
    Optional<Invoice> findByCheckoutToken(String checkoutToken);
    @EntityGraph(attributePaths={"branch","cashier"})
    @Query("""
        select i from Invoice i where (:branch is null or i.branch.id=:branch)
        and (:cashier is null or i.cashier.id=:cashier)
        and (:start is null or i.createdAt>=:start) and (:end is null or i.createdAt<:end)
        and (:code is null or lower(i.code) like :code escape '!')
        and (:phone is null or i.customerPhone like :phone escape '!')
        and (:status is null or i.status=:status)
        """)
    org.springframework.data.domain.Page<Invoice> search(
        @org.springframework.data.repository.query.Param("branch") Long branch,
        @org.springframework.data.repository.query.Param("cashier") Long cashier,
        @org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
        @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end,
        @org.springframework.data.repository.query.Param("code") String code,
        @org.springframework.data.repository.query.Param("phone") String phone,
        @org.springframework.data.repository.query.Param("status") com.dienmay.sales.entity.InvoiceStatus status,
        org.springframework.data.domain.Pageable pageable);
}

