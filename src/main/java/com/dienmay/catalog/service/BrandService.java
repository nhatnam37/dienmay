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
public class BrandService {
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final BrandMapper mapper;
    private final CatalogFormValidator formValidator;

    public BrandService(BrandRepository brandRepository, ProductRepository productRepository,
                        BrandMapper mapper, CatalogFormValidator formValidator) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.mapper = mapper;
        this.formValidator = formValidator;
    }

    @Transactional
    public BrandView create(BrandForm form) {
        normalizeAndValidate(form);
        if (brandRepository.existsByName(form.getName())) {
            throw new BusinessException("Tên thương hiệu đã tồn tại.");
        }
        Brand brand = new Brand();
        mapper.update(form, brand);
        return save(brand);
    }

    @Transactional
    public BrandView update(Long id, BrandForm form) {
        Brand brand = find(id);
        normalizeAndValidate(form);
        if (brandRepository.existsByNameAndIdNot(form.getName(), id)) {
            throw new BusinessException("Tên thương hiệu đã tồn tại.");
        }
        mapper.update(form, brand);
        return save(brand);
    }

    @Transactional
    public void delete(Long id) {
        Brand brand = find(id);
        if (productRepository.countByBrandId(id) > 0) {
            throw new BusinessException("Không thể xóa thương hiệu còn sản phẩm.");
        }
        try {
            brandRepository.delete(brand);
            brandRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Không thể xóa thương hiệu đang được sử dụng.");
        }
    }

    public BrandForm getForm(Long id) {
        return mapper.toForm(find(id));
    }

    public List<BrandView> list() {
        return brandRepository.findAll(Sort.by("name").ascending()).stream().map(mapper::toView).toList();
    }

    private void normalizeAndValidate(BrandForm form) {
        form.setName(formValidator.trim(form.getName()));
        formValidator.validate(form);
    }

    private Brand find(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu: " + id));
    }

    private BrandView save(Brand brand) {
        try {
            return mapper.toView(brandRepository.saveAndFlush(brand));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Không thể lưu thương hiệu vì tên đã tồn tại.");
        }
    }
}
