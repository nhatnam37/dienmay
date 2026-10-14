package com.dienmay.organization.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice(basePackages = "com.dienmay.organization.web")
public class OrganizationBindingAdvice {
    @InitBinder
    public void bind(WebDataBinder binder) { binder.registerCustomEditor(String.class, new StringTrimmerEditor(true)); }
}
