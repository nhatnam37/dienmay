package com.dienmay.customer.repository;
import com.dienmay.customer.entity.Customer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CustomerRepository extends JpaRepository<Customer,Long> {
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone,Long id);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select c from Customer c where c.id=:id")
    Optional<Customer> findForUpdate(@Param("id") Long id);
}
