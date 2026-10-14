package com.dienmay.catalog.service;

import com.dienmay.catalog.dto.*;
import com.dienmay.catalog.entity.*;
import com.dienmay.catalog.mapper.*;
import com.dienmay.catalog.repository.*;
import com.dienmay.common.exception.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.util.*;
import static com.dienmay.catalog.service.CatalogTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock private ProductRepository products;
    @Mock private CategoryRepository categories;
    @Mock private BrandRepository brands;
    private ProductService service;
    private static ValidatorFactory validatorFactory;
    private CatalogFormValidator validator;

    @BeforeAll
    static void openValidator() { validatorFactory = Validation.buildDefaultValidatorFactory(); }

    @AfterAll
    static void closeValidator() { validatorFactory.close(); }

    @BeforeEach
    void setUp() {
        validator = new CatalogFormValidator(validatorFactory.getValidator());
        service = new ProductService(products, categories, brands, new ProductMapper(), validator);
    }

    @Test
    void create_duplicateNormalizedSku_rejectsRequest() {
        when(products.existsBySku("IP-15")).thenReturn(true);
        assertThatThrownBy(() -> service.create(productForm())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("SKU đã tồn tại");
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void create_missingCategory_throwsNotFound() {
        assertThatThrownBy(() -> service.create(productForm())).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("danh mục");
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void create_missingBrand_throwsNotFound() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1, "Điện thoại", null)));
        assertThatThrownBy(() -> service.create(productForm())).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("thương hiệu");
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void create_lowercasePaddedSku_savesUppercaseAndReturnsNames() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1, "Điện thoại", null)));
        when(brands.findById(2L)).thenReturn(Optional.of(brand(2, "Apple")));
        when(products.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        ProductView view = service.create(productForm());
        verify(products).existsBySku("IP-15");
        verify(products).saveAndFlush(argThat(p -> p.getSku().equals("IP-15")));
        assertThat(view.getSku()).isEqualTo("IP-15");
        assertThat(view.getCategoryName()).isEqualTo("Điện thoại");
        assertThat(view.getBrandName()).isEqualTo("Apple");
        assertThat(view.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void update_serialFlagChanged_checksGuardAndPreservesStatus() {
        Product product = product();
        product.setStatus(ProductStatus.DISCONTINUED);
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(categories.findById(1L)).thenReturn(Optional.of(product.getCategory()));
        when(brands.findById(2L)).thenReturn(Optional.of(product.getBrand()));
        when(products.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        ProductService checked = spy(service);
        ProductView result = checked.update(3L, productForm());
        verify(checked).assertTracksSerialChangeAllowed(3L);
        verify(products).existsBySkuAndIdNot("IP-15", 3L);
        assertThat(result.isTracksSerial()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        assertThat(result.getSku()).isEqualTo("IP-15");
    }

    @Test
    void discontinue_existingProduct_onlyChangesStatusWithoutDelete() {
        Product product = product();
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(products.saveAndFlush(product)).thenReturn(product);
        ProductView result = service.discontinue(3L);
        assertThat(result.getStatus()).isEqualTo(ProductStatus.DISCONTINUED);
        assertThat(result.getSku()).isEqualTo("IP-15");
        assertThat(result.getName()).isEqualTo("iPhone 15");
        verify(products).findById(3L);
        verify(products).saveAndFlush(product);
        // Không chỉ delete(entity): verifyNoMoreInteractions chặn cả deleteById/batch.
        verifyNoMoreInteractions(products);
    }

    @Test
    void reactivate_discontinuedProduct_changesToActive() {
        Product product = product();
        product.setStatus(ProductStatus.DISCONTINUED);
        when(products.findById(3L)).thenReturn(Optional.of(product));
        when(products.saveAndFlush(product)).thenReturn(product);
        assertThat(service.reactivate(3L).getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void getById_missingProduct_throwsNotFound() {
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void search_optionalFilters_mapsPageAndEscapesLikeCharacters() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("name"));
        when(products.search("%TV!%!_%", 1L, 2L, ProductStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(product()), pageable, 1));
        Page<ProductView> result = service.search(" TV%_ ", 1L, 2L, ProductStatus.ACTIVE, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getBrandName()).isEqualTo("Apple");
    }

    @Test
    void search_blankKeyword_passesNullFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        when(products.search(null, null, null, null, pageable)).thenReturn(Page.empty(pageable));
        assertThat(service.search("  ", null, null, null, pageable)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-IP", "IP-", "IP--15", "IP 15", "IP_15", ""})
    void create_invalidSku_rejectsRequest(String sku) {
        ProductForm form = productForm();
        form.setSku(sku);
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(products, categories, brands);
    }

    @Test
    void create_negativePrice_rejectsRequest() {
        ProductForm form = productForm();
        form.setPrice(new BigDecimal("-0.01"));
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giá");
    }

    @Test
    void create_warrantyOverSixty_rejectsRequest() {
        ProductForm form = productForm();
        form.setWarrantyMonths(61);
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("60");
    }
}
