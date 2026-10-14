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
class CategoryServiceTest {
    @Mock private CategoryRepository categories;
    @Mock private ProductRepository products;
    private CategoryService service;
    private static ValidatorFactory validatorFactory;
    private CatalogFormValidator validator;

    @BeforeAll
    static void openValidator() { validatorFactory = Validation.buildDefaultValidatorFactory(); }

    @AfterAll
    static void closeValidator() { validatorFactory.close(); }

    @BeforeEach
    void setUp() {
        validator = new CatalogFormValidator(validatorFactory.getValidator());
        service = new CategoryService(categories, products, new CategoryMapper(), validator);
    }

    @Test
    void create_duplicateName_rejectsRequest() {
        when(categories.existsByName("TIVI")).thenReturn(true);
        assertThatThrownBy(() -> service.create(categoryForm(" TIVI ", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void update_selfAsParent_rejectsRequest() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1, "Tivi", null)));
        assertThatThrownBy(() -> service.update(1L, categoryForm("Tivi", 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chính");
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void update_descendantAsParent_rejectsRequest() {
        Category root = category(1, "Gốc", null);
        Category child = category(2, "Con", root);
        Category grandchild = category(3, "Cháu", child);
        when(categories.findById(1L)).thenReturn(Optional.of(root));
        when(categories.findById(3L)).thenReturn(Optional.of(grandchild));
        assertThatThrownBy(() -> service.update(1L, categoryForm("Gốc", 3L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("con/cháu");
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void delete_categoryHasProducts_rejectsRequest() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1, "Tivi", null)));
        when(products.countByCategoryId(1L)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("còn sản phẩm");
        verify(categories, never()).delete(any(Category.class));
    }

    @Test
    void delete_categoryHasChildren_rejectsRequest() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1, "Tivi", null)));
        when(categories.existsByParentId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("danh mục con");
        verify(categories, never()).delete(any(Category.class));
    }

    @Test
    void delete_emptyCategory_deletesSuccessfully() {
        Category empty = category(1, "Tivi", null);
        when(categories.findById(1L)).thenReturn(Optional.of(empty));
        service.delete(1L);
        verify(categories).delete(empty);
        verify(categories).flush();
    }

    @Test
    void create_validParent_returnsViewWithParentName() {
        Category parent = category(1, "Điện tử", null);
        when(categories.findById(1L)).thenReturn(Optional.of(parent));
        when(categories.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        CategoryView result = service.create(categoryForm(" Tivi ", 1L));
        assertThat(result.getName()).isEqualTo("Tivi");
        assertThat(result.getParentId()).isEqualTo(1L);
        assertThat(result.getParentName()).isEqualTo("Điện tử");
    }

    @Test
    void update_sameNameOnSameCategory_allowsUpdate() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1, "Tivi", null)));
        when(categories.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.update(1L, categoryForm("Tivi", null)).getName()).isEqualTo("Tivi");
        verify(categories).existsByNameAndIdNot("Tivi", 1L);
    }

    @Test
    void create_blankName_rejectsRequest() {
        assertThatThrownBy(() -> service.create(categoryForm("  ", null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không được để trống");
        verifyNoInteractions(categories);
    }

    @Test
    void delete_missingCategory_throwsNotFound() {
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_categories_mapsParentWithoutExposingEntity() {
        Category parent = category(1, "Điện tử", null);
        when(categories.findAll(any(Sort.class))).thenReturn(List.of(category(2, "Tivi", parent)));
        assertThat(service.list()).singleElement().satisfies(view -> {
            assertThat(view.getName()).isEqualTo("Tivi");
            assertThat(view.getParentName()).isEqualTo("Điện tử");
        });
    }
}
