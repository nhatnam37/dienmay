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
@ExtendWith(MockitoExtension.class)
class BrandServiceTest {
    @Mock private BrandRepository brands;
    @Mock private ProductRepository products;
    private BrandService service;
    private static ValidatorFactory validatorFactory;
    private CatalogFormValidator validator;

    @BeforeAll
    static void openValidator() { validatorFactory = Validation.buildDefaultValidatorFactory(); }

    @AfterAll
    static void closeValidator() { validatorFactory.close(); }

    @BeforeEach
    void setUp() {
        validator = new CatalogFormValidator(validatorFactory.getValidator());
        service = new BrandService(brands, products, new BrandMapper(), validator);
    }

    @Test
    @com.dienmay.support.TestCase("TC-BRD-01")
    void delete_brandHasProducts_rejectsRequest() {
        when(brands.findById(1L)).thenReturn(Optional.of(brand(1, "Apple")));
        when(products.countByBrandId(1L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("còn sản phẩm");
        verify(brands, never()).delete(any(Brand.class));
    }

    @Test
    void create_duplicateName_rejectsRequest() {
        when(brands.existsByName("APPLE")).thenReturn(true);
        assertThatThrownBy(() -> service.create(brandForm(" APPLE ")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");
        verify(brands, never()).saveAndFlush(any());
    }

    @Test
    void create_validName_trimsAndReturnsView() {
        when(brands.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.create(brandForm(" Apple ")).getName()).isEqualTo("Apple");
    }

    @Test
    void update_sameNameOnSameBrand_allowsUpdate() {
        when(brands.findById(1L)).thenReturn(Optional.of(brand(1, "Apple")));
        when(brands.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(1L, brandForm("Apple")).getName()).isEqualTo("Apple");
        verify(brands).existsByNameAndIdNot("Apple", 1L);
    }

    @Test
    void delete_emptyBrand_deletesSuccessfully() {
        Brand empty = brand(1, "Apple");
        when(brands.findById(1L)).thenReturn(Optional.of(empty));
        service.delete(1L);
        verify(brands).delete(empty);
        verify(brands).flush();
    }

    @Test
    void list_brands_returnsViews() {
        when(brands.findAll(any(Sort.class))).thenReturn(List.of(brand(1, "Apple")));
        assertThat(service.list()).singleElement().extracting(BrandView::getName).isEqualTo("Apple");
    }
}
