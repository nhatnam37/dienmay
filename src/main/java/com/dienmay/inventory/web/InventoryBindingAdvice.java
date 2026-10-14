package com.dienmay.inventory.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice(basePackages = "com.dienmay.inventory.web")
public class InventoryBindingAdvice {
    @InitBinder
    public void bind(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(1000);
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
        binder.setDisallowedFields("id", "code", "status", "createdBy", "createdAt");
    }
}
