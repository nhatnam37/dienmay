package com.dienmay.orders.repository;
import com.dienmay.orders.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.*;
public interface OrderRepository extends JpaRepository<CustomerOrder,Long> {
    @EntityGraph(attributePaths={"branch"})
    @Query("""
        select o from CustomerOrder o where (:branch is null or o.branch.id=:branch)
        and (:status is null or o.status=:status)
        and (:start is null or o.createdAt>=:start) and (:end is null or o.createdAt<:end)
        and (:code is null or lower(o.code) like :code escape '!')
        and (:phone is null or o.receiverPhone like :phone escape '!')
        order by case when o.expiresAt is null then 1 else 0 end,o.expiresAt,o.createdAt desc,o.id desc
        """)
    org.springframework.data.domain.Page<CustomerOrder> searchStaff(@Param("branch") Long branch,@Param("status") OrderStatus status,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end,@Param("code") String code,@Param("phone") String phone,Pageable page);
    long countByBranchIdAndStatus(Long branchId,OrderStatus status);
    @Query("select o.id from CustomerOrder o where o.code=:code and o.customer.id=:customer")
    Optional<Long> findOwnedId(@Param("code") String code,@Param("customer") Long customer);
    @EntityGraph(attributePaths={"branch"})
    @Query("select o from CustomerOrder o where o.customer.id=:customer and (:status is null or o.status=:status)")
    org.springframework.data.domain.Page<CustomerOrder> searchCustomer(@Param("customer") Long customer,@Param("status") OrderStatus status,Pageable page);
    Optional<CustomerOrder> findByPlaceToken(String token);
    @EntityGraph(attributePaths={"branch","customer"}) Optional<CustomerOrder> findByCodeAndCustomerId(String code,Long customerId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id=:id") Optional<CustomerOrder> findForUpdate(@Param("id") Long id);
    @Query("select o.id from CustomerOrder o where o.status in (com.dienmay.orders.entity.OrderStatus.PENDING_PAYMENT,com.dienmay.orders.entity.OrderStatus.PLACED) and o.expiresAt<:now order by o.expiresAt,o.id")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now,Pageable page);
    @EntityGraph(attributePaths={"branch"}) List<CustomerOrder> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId,Pageable page);
}
