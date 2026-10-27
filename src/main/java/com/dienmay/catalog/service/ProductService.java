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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductMapper mapper;
    private final CatalogFormValidator formValidator;
    private final ProductInventoryGuard inventoryGuard;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          BrandRepository brandRepository, ProductMapper mapper, CatalogFormValidator formValidator, ProductInventoryGuard inventoryGuard) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.mapper = mapper;
        this.formValidator = formValidator;
        this.inventoryGuard = inventoryGuard;
    }

    @Transactional
    public ProductView create(ProductForm form) {
        normalizeAndValidate(form);
        if (productRepository.existsBySku(form.getSku())) {
            throw new BusinessException("SKU đã tồn tại.");
        }
        Category category = findCategory(form.getCategoryId());
        Brand brand = findBrand(form.getBrandId());
        Product product = new Product();
        mapper.update(form, product, category, brand);
        return save(product);
    }

    @Transactional
    public ProductView update(Long id, ProductForm form) {
        // Cùng khóa product như nghiệp vụ nhập kho: không đổi loại theo dõi
        // sau khi request nhập hàng khác đã kiểm tra loại sản phẩm.
        Product product = productRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
        normalizeAndValidate(form);
        if (productRepository.existsBySkuAndIdNot(form.getSku(), id)) {
            throw new BusinessException("SKU đã tồn tại.");
        }
        Category category = findCategory(form.getCategoryId());
        Brand brand = findBrand(form.getBrandId());
        if (product.isTracksSerial() != form.getTracksSerial() || product.getSerialType() != form.getSerialType()) {
            assertTracksSerialChangeAllowed(id);
        }
        mapper.update(form, product, category, brand);
        // Mapper không đổi status: chỉ discontinue/reactivate được thay trạng thái.
        return save(product);
    }

    public Page<ProductView> search(String keyword, Long categoryId, Long brandId,
                                    ProductStatus status, Pageable pageable) {
        String pattern = com.dienmay.common.pagination.SearchTerms.contains(keyword,false);
        // Map trong transaction: view không mang lazy proxy ra ngoài tầng service.
        return productRepository.search(pattern, categoryId, brandId, status, pageable).map(mapper::toView);
    }

    @Transactional
    public ProductView getByIdForUpdate(Long id) {
        return mapper.toView(productRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id)));
    }

    public ProductView getById(Long id) {
        return mapper.toView(find(id));
    }

    @Transactional
    public ProductView discontinue(Long id) {
        Product product = find(id);
        product.setStatus(ProductStatus.DISCONTINUED);
        // Giữ nguyên dòng để lịch sử bán hàng, bảo hành có thể tham chiếu sản phẩm.
        return save(product);
    }

    @Transactional
    public ProductView reactivate(Long id) {
        Product product = find(id);
        product.setStatus(ProductStatus.ACTIVE);
        return save(product);
    }

    public void assertTracksSerialChangeAllowed(Long productId) {
        // Catalog chỉ phụ thuộc interface; inventory quyết định dữ liệu nào là dữ liệu kho.
        if (inventoryGuard.hasInventoryData(productId)) {
            throw new BusinessException("Không được đổi theo dõi Serial hoặc loại Serial khi sản phẩm đã có dữ liệu kho.");
        }
    }

    private void normalizeAndValidate(ProductForm form) {
        // Chuẩn hóa tại service TRƯỚC validation, kiểm tra trùng và lưu.
        String sku = formValidator.trim(form.getSku());
        form.setSku(sku == null ? null : sku.toUpperCase(java.util.Locale.ROOT));
        form.setName(formValidator.trim(form.getName()));
        form.setImageUrl(formValidator.trim(form.getImageUrl()));
        formValidator.validate(form);
        if(form.getPrice().stripTrailingZeros().scale()>0)throw new BusinessException("Giá sản phẩm phải là số nguyên đồng.");
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm: " + id));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục: " + id));
    }

    private Brand findBrand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu: " + id));
    }

    private ProductView save(Product product) {
        try {
            return mapper.toView(productRepository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException("Không thể lưu sản phẩm: SKU trùng hoặc dữ liệu tham chiếu không còn hợp lệ.");
        }
    }
}
