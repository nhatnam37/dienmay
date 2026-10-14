package com.dienmay.catalog.web;

import com.dienmay.catalog.dto.*;
import com.dienmay.catalog.entity.ProductStatus;
import com.dienmay.catalog.service.*;
import com.dienmay.common.exception.BusinessException;
import com.dienmay.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BindingResult;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ProductController.class, CategoryController.class, BrandController.class})
@Import(SecurityConfig.class)
class CatalogWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService products;
    @MockitoBean ProductAdminService admin;
    @MockitoBean CategoryService categories;
    @MockitoBean BrandService brands;

    private ProductView product() {
        return new ProductView(1L, 2L, "Điện thoại", 3L, "Apple", "IP-15", "iPhone 15", "Mô tả",
                new BigDecimal("1000000"), true, 12, null, ProductStatus.ACTIVE, null, null);
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void products_cashier_isForbidden() throws Exception {
        mvc.perform(get("/admin/catalog/products")).andExpect(status().isForbidden());
        verifyNoInteractions(products);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void products_admin_rendersPageWithFiltersAndPrice() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        when(products.search("iphone", 2L, 3L, ProductStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(product()), pageable, 11));
        mvc.perform(get("/admin/catalog/products").param("keyword", "iphone").param("categoryId", "2")
                        .param("brandId", "3").param("status", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(view().name("catalog/products/list"))
                .andExpect(content().string(containsString("1.000.000 đ")))
                .andExpect(content().string(containsString("keyword=iphone")))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("categoryId=2")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_missingCsrf_isForbidden() throws Exception {
        mvc.perform(post("/admin/catalog/products")).andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_invalidForm_redirectsWithFieldErrors() throws Exception {
        var result = mvc.perform(post("/admin/catalog/products").with(csrf()).param("name", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/catalog/products/new")).andReturn();
        BindingResult errors = (BindingResult) result.getFlashMap().get(BindingResult.MODEL_KEY_PREFIX + "form");
        assertThat(errors.hasFieldErrors("name")).isTrue();
        mvc.perform(get("/admin/catalog/products/new").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Tên sản phẩm không được để trống")));
        verifyNoInteractions(admin);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_validForm_redirectsWithSuccess() throws Exception {
        mvc.perform(post("/admin/catalog/products").with(csrf()).param("sku", "ip-15").param("name", "iPhone")
                        .param("categoryId", "2").param("brandId", "3").param("price", "1000000")
                        .param("tracksSerial", "true").param("warrantyMonths", "12"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/catalog/products"))
                .andExpect(flash().attributeExists("success"));
        verify(admin).create(any(ProductEditForm.class), isNull());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_duplicateName_redirectsWithBusinessError() throws Exception {
        when(categories.create(any())).thenThrow(new BusinessException("Tên danh mục đã tồn tại."));
        var result = mvc.perform(post("/admin/catalog/categories").with(csrf()).param("name", "Tivi"))
                .andExpect(redirectedUrl("/admin/catalog/categories/new")).andReturn();
        mvc.perform(get("/admin/catalog/categories/new").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Tên danh mục đã tồn tại.")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteBrand_hasProducts_redirectsWithFailureFlash() throws Exception {
        doThrow(new BusinessException("Không thể xóa thương hiệu còn sản phẩm.")).when(brands).delete(1L);
        mvc.perform(post("/admin/catalog/brands/1/delete").with(csrf()))
                .andExpect(redirectedUrl("/admin/catalog/brands"))
                .andExpect(flash().attribute("error", "Không thể xóa thương hiệu còn sản phẩm."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void detail_existingProduct_rendersDetails() throws Exception {
        when(products.getById(1L)).thenReturn(product());
        mvc.perform(get("/admin/catalog/products/1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("iPhone 15")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void edit_existingProduct_rendersMultipartForm() throws Exception {
        when(products.getById(1L)).thenReturn(product());
        mvc.perform(get("/admin/catalog/products/1/edit"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("multipart/form-data")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void categories_admin_rendersList() throws Exception {
        when(categories.list()).thenReturn(List.of(new CategoryView(1L, "Tivi", null, null, null)));
        mvc.perform(get("/admin/catalog/categories")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tivi")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void editCategoryAndBrand_existingItems_renderForms() throws Exception {
        CategoryForm category = new CategoryForm();
        category.setName("Tivi");
        when(categories.getForm(1L)).thenReturn(category);
        BrandForm brand = new BrandForm();
        brand.setName("Apple");
        when(brands.getForm(1L)).thenReturn(brand);
        mvc.perform(get("/admin/catalog/categories/1/edit")).andExpect(status().isOk());
        mvc.perform(get("/admin/catalog/brands/1/edit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createProduct_invalidNumericInput_rendersVietnameseFieldError() throws Exception {
        var result = mvc.perform(post("/admin/catalog/products").with(csrf()).param("price", "abc"))
                .andExpect(redirectedUrl("/admin/catalog/products/new")).andReturn();
        mvc.perform(get("/admin/catalog/products/new").flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString("Giá phải là một số hợp lệ.")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void brands_admin_rendersListAndNewForm() throws Exception {
        when(brands.list()).thenReturn(List.of(new BrandView(1L, "Apple", null)));
        mvc.perform(get("/admin/catalog/brands")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Apple")));
        mvc.perform(get("/admin/catalog/brands/new")).andExpect(status().isOk());
    }
}
