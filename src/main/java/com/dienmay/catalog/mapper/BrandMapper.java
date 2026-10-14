package com.dienmay.catalog.mapper;

import com.dienmay.catalog.dto.BrandForm;
import com.dienmay.catalog.dto.BrandView;
import com.dienmay.catalog.entity.*;
import org.springframework.stereotype.Component;

@Component
public class BrandMapper {
    public BrandForm toForm(Brand entity) {
        BrandForm form = new BrandForm();
        form.setName(entity.getName());
        return form;
    }

    public void update(BrandForm form, Brand target) {
        target.setName(form.getName());
    }

    public BrandView toView(Brand entity) {
        return new BrandView(entity.getId(), entity.getName(), entity.getCreatedAt());
    }
}
