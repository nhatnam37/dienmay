package com.dienmay.catalog.service;

import com.dienmay.catalog.dto.*;
import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.mapper.*;
import com.dienmay.catalog.repository.*;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper mapper;
    private final CatalogFormValidator formValidator;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository,
                           CategoryMapper mapper, CatalogFormValidator formValidator) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.formValidator = formValidator;
    }

    @Transactional
    public CategoryView create(CategoryForm form) {
        normalizeAndValidate(form);
        if (categoryRepository.existsByName(form.getName())) {
            throw new BusinessException("Tên danh mục đã tồn tại.");
        }
        Category parent = resolveParent(null, form.getParentId());
        Category category = new Category();
        mapper.update(form, category, parent);
        return save(category);
    }

    @Transactional
    public CategoryView update(Long id, CategoryForm form) {
        // Cây danh mục nhỏ: khóa trước khi đọc để kiểm tra vòng lặp còn đúng
        // khi hai request đổi cha cùng lúc (A -> B và B -> A).
        categoryRepository.findAllForHierarchyUpdate();
        Category category = find(id);
        normalizeAndValidate(form);
        if (categoryRepository.existsByNameAndIdNot(form.getName(), id)) {
            throw new BusinessException("Tên danh mục đã tồn tại.");
        }
        Category parent = resolveParent(id, form.getParentId());
        mapper.update(form, category, parent);
        return save(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new BusinessException("Không thể xóa danh mục còn sản phẩm.");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException("Không thể xóa danh mục còn danh mục con.");
        }
        try {
            categoryRepository.delete(category);
            // Flush để lỗi FK do dữ liệu phát sinh đồng thời xuất hiện ngay tại đây.
            categoryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Không thể xóa danh mục đang được sử dụng.");
        }
    }

    public CategoryForm getForm(Long id) {
        return mapper.toForm(find(id));
    }

    public List<CategoryView> list() {
        return categoryRepository.findAll(Sort.by("name").ascending()).stream().map(mapper::toView).toList();
    }

    private void normalizeAndValidate(CategoryForm form) {
        form.setName(formValidator.trim(form.getName()));
        formValidator.validate(form);
    }

    private Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục: " + id));
    }

    private Category resolveParent(Long id, Long parentId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(id)) {
            throw new BusinessException("Không được chọn chính danh mục làm cha.");
        }
        Category parent = find(parentId);
        // Đi ngược từ cha dự kiến lên gốc: gặp chính mình nghĩa là tạo chu trình.
        // Set còn giúp thoát an toàn nếu dữ liệu cũ đã có vòng lặp.
        java.util.Set<Long> visited = new java.util.HashSet<>();
        for (Category current = parent; current != null; current = current.getParent()) {
            if (current.getId().equals(id) || !visited.add(current.getId())) {
                throw new BusinessException("Không được chọn danh mục con/cháu làm cha hoặc tạo vòng lặp.");
            }
        }
        return parent;
    }

    private CategoryView save(Category category) {
        try {
            // Unique constraint là lớp bảo vệ cuối nếu hai request cùng vượt qua exists.
            return mapper.toView(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Không thể lưu danh mục: tên trùng hoặc danh mục cha không còn tồn tại.");
        }
    }
}
