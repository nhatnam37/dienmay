package com.dienmay.catalog.repository;

import com.dienmay.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    // So sánh bằng collation as_ci của DB: không đổi lower/upper trong Java.
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByParentId(Long parentId);

    // Khóa cây theo cùng thứ tự để hai thao tác đổi cha đồng thời không tạo vòng lặp.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c order by c.id")
    List<Category> findAllForHierarchyUpdate();

    @Override
    @EntityGraph(attributePaths = "parent")
    List<Category> findAll(Sort sort);
}
