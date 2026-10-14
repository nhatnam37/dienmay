package com.dienmay.organization.repository;

import com.dienmay.organization.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // join fetch: nạp role và branch trong một câu SQL, tránh N+1 và lỗi LazyInitializationException.
    @Query("""
            select e from Employee e
            join fetch e.role
            join fetch e.branch
            where e.username = :username
            """)
    Optional<Employee> findWithRoleAndBranchByUsername(@Param("username") String username);

    boolean existsByUsername(String username);
}
