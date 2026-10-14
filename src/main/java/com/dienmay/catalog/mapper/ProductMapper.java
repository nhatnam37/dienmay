package com.dienmay.catalog.mapper;

import com.dienmay.catalog.dto.ProductForm;
import com.dienmay.catalog.dto.ProductView;
import com.dienmay.catalog.entity.*;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {
    public void update(ProductForm form, Product target, Category category, Brand brand) {
        target.setCategory(category);
        target.setBrand(brand);
        target.setSku(form.getSku());
        target.setName(form.getName());
        target.setDescription(form.getDescription());
        target.setPrice(form.getPrice());
        target.setTracksSerial(form.getTracksSerial());
        target.setWarrantyMonths(form.getWarrantyMonths());
        target.setImageUrl(form.getImageUrl());

    }

    public ProductView toView(Product entity) {
        return new ProductView(entity.getId(), entity.getCategory().getId(), entity.getCategory().getName(),
                entity.getBrand().getId(), entity.getBrand().getName(), entity.getSku(), entity.getName(),
                entity.getDescription(), entity.getPrice(), entity.isTracksSerial(), entity.getWarrantyMonths(),
                entity.getImageUrl(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
