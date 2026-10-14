package com.dienmay.catalog.service;

import com.dienmay.catalog.dto.*;
import com.dienmay.catalog.entity.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;

final class CatalogTestData {
    private CatalogTestData() { }

    static Category category(long id, String name, Category parent) {
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", id);
        category.setName(name);
        category.setParent(parent);
        return category;
    }

    static Brand brand(long id, String name) {
        Brand brand = new Brand();
        ReflectionTestUtils.setField(brand, "id", id);
        brand.setName(name);
        return brand;
    }

    static Product product() {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 3L);
        product.setSku("IP-15");
        product.setName("iPhone 15");
        product.setPrice(new BigDecimal("15000000.00"));
        product.setCategory(category(1L, "Điện thoại", null));
        product.setBrand(brand(2L, "Apple"));
        return product;
    }

    static CategoryForm categoryForm(String name, Long parentId) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setParentId(parentId);
        return form;
    }

    static BrandForm brandForm(String name) {
        BrandForm form = new BrandForm();
        form.setName(name);
        return form;
    }

    static ProductForm productForm() {
        ProductForm form = new ProductForm();
        form.setCategoryId(1L);
        form.setBrandId(2L);
        form.setSku("  ip-15  ");
        form.setName(" iPhone 15 ");
        form.setPrice(new BigDecimal("15000000.00"));
        form.setTracksSerial(true);
        form.setSerialType(SerialType.GENERIC);
        form.setWarrantyMonths(12);
        return form;
    }
}
