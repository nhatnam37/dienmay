package com.dienmay.catalog.mapper;

import com.dienmay.catalog.dto.CategoryForm;
import com.dienmay.catalog.dto.CategoryView;
import com.dienmay.catalog.entity.*;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    public CategoryForm toForm(Category entity) {
        CategoryForm form = new CategoryForm();
        form.setName(entity.getName());
        form.setParentId(entity.getParent() == null ? null : entity.getParent().getId());
        return form;
    }

    public void update(CategoryForm form, Category target, Category parent) {
        target.setName(form.getName());
        target.setParent(parent);
    }

    public CategoryView toView(Category entity) {
        Category parent = entity.getParent();
        return new CategoryView(entity.getId(), entity.getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(), entity.getCreatedAt());
    }
}
