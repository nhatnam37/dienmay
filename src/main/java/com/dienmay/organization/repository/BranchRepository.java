package com.dienmay.organization.repository;

import com.dienmay.organization.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByCode(String code);
}
