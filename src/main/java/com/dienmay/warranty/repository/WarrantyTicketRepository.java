package com.dienmay.warranty.repository;
import com.dienmay.warranty.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface WarrantyTicketRepository extends JpaRepository<WarrantyTicket,Long>, JpaSpecificationExecutor<WarrantyTicket> {
    @Query("select count(t)>0 from WarrantyTicket t where t.serialItem.id=:id and t.status not in (com.dienmay.warranty.entity.WarrantyTicketStatus.RETURNED,com.dienmay.warranty.entity.WarrantyTicketStatus.CANCELLED)")
    boolean existsOpen(@Param("id") Long serialId);
    @Query("select t.serialItem.id from WarrantyTicket t where t.id=:id")
    Optional<Long> findSerialIdentity(@Param("id") Long id);
    @EntityGraph(attributePaths={"serialItem","serialItem.product","branch","warranty"})
    org.springframework.data.domain.Page<WarrantyTicket> findAll(org.springframework.data.jpa.domain.Specification<WarrantyTicket> spec, org.springframework.data.domain.Pageable pageable);
    List<WarrantyTicket> findAllByWarrantyIdOrderByReceivedAtAscIdAsc(Long warrantyId);
    Optional<WarrantyTicket> findByCodeAndSerialItemSerialNo(String code,String serialNo);
}
