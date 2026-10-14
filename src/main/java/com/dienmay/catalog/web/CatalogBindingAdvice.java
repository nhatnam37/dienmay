package com.dienmay.catalog.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice(basePackages = "com.dienmay.catalog.web")
public class CatalogBindingAdvice {
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // Trim trước @Valid; chuẩn hóa chữ hoa SKU vẫn do service chịu trách nhiệm.
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        binder.setDisallowedFields("id", "imageUrl", "status", "createdAt", "updatedAt");
    }
}
