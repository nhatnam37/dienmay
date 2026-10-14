package com.dienmay.catalog.web;

import com.dienmay.catalog.dto.CategoryForm;
import com.dienmay.catalog.service.CategoryService;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/catalog/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", service.list());
        return "catalog/categories/list";
    }

    @GetMapping("/new")
    public String add(Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", new CategoryForm()); }
        return prepareForm(null, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", service.getForm(id)); }
        return prepareForm(id, model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CategoryForm form, BindingResult errors,
                         RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                service.create(form);
                flash.addFlashAttribute("success", "Đã thêm danh mục.");
                return "redirect:/admin/catalog/categories";
            } catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return formError(form, errors, flash, "/new");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") CategoryForm form,
                         BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                service.update(id, form);
                flash.addFlashAttribute("success", "Đã cập nhật danh mục.");
                return "redirect:/admin/catalog/categories";
            } catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return formError(form, errors, flash, "/" + id + "/edit");
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        try {
            service.delete(id);
            flash.addFlashAttribute("success", "Đã xóa danh mục.");
        } catch (BusinessException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/admin/catalog/categories";
    }

    private String prepareForm(Long id, Model model) {
        model.addAttribute("id", id);
        model.addAttribute("parents", service.list());
        return "catalog/categories/form";
    }

    private String formError(CategoryForm form, BindingResult errors, RedirectAttributes flash, String suffix) {
        flash.addFlashAttribute("form", form);
        flash.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", errors);
        flash.addFlashAttribute("error", "Không thể lưu. Vui lòng kiểm tra thông tin.");
        return "redirect:/admin/catalog/categories" + suffix;
    }
}
