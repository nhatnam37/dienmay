package com.dienmay.organization.web;

import com.dienmay.organization.dto.BranchForm;
import com.dienmay.organization.service.BranchService;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/organization/branches")
public class BranchController {
    private final BranchService service;

    public BranchController(BranchService service) { this.service = service; }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", service.list());
        return "organization/branches/list";
    }

    @GetMapping("/new")
    public String add(Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", new BranchForm()); }
        return prepareForm(null, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", service.getForm(id)); }
        return prepareForm(id, model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BranchForm form, BindingResult errors,
                         RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                service.create(form);
                flash.addFlashAttribute("success", "Đã thêm chi nhánh.");
                return "redirect:/admin/organization/branches";
            } catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return formError(form, errors, flash, "/new");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BranchForm form,
                         BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                service.update(id, form);
                flash.addFlashAttribute("success", "Đã cập nhật chi nhánh.");
                return "redirect:/admin/organization/branches";
            } catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return formError(form, errors, flash, "/" + id + "/edit");
    }

    @PostMapping("/{id}/active")
    public String active(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        service.setActive(id, active);
        flash.addFlashAttribute("success", active ? "Đã mở lại chi nhánh." : "Đã ngừng hoạt động chi nhánh. Dữ liệu lịch sử được giữ nguyên.");
        return "redirect:/admin/organization/branches";
    }

    private String prepareForm(Long id, Model model) {
        model.addAttribute("id", id);
        return "organization/branches/form";
    }

    private String formError(BranchForm form, BindingResult errors, RedirectAttributes flash, String suffix) {
        flash.addFlashAttribute("form", form);
        flash.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", errors);
        flash.addFlashAttribute("error", "Không thể lưu. Vui lòng kiểm tra thông tin.");
        return "redirect:/admin/organization/branches" + suffix;
    }
}
