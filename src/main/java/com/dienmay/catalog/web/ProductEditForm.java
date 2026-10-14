package com.dienmay.catalog.web;

import com.dienmay.catalog.dto.ProductForm;
import com.dienmay.catalog.dto.ProductView;
import org.springframework.web.multipart.MultipartFile;

public class ProductEditForm extends ProductForm {
    private MultipartFile image;

    public MultipartFile getImage() { return image; }
    public void setImage(MultipartFile image) { this.image = image; }

    public static ProductEditForm from(ProductView view) {
        ProductEditForm form = new ProductEditForm();
        form.setCategoryId(view.getCategoryId());
        form.setBrandId(view.getBrandId());
        form.setSku(view.getSku());
        form.setName(view.getName());
        form.setDescription(view.getDescription());
        form.setPrice(view.getPrice());
        form.setTracksSerial(view.isTracksSerial());
        form.setSerialType(view.getSerialType());
        form.setWarrantyMonths(view.getWarrantyMonths());
        form.setImageUrl(view.getImageUrl());
        return form;
    }
}
