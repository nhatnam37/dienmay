package com.dienmay.inventory.web;

import com.dienmay.inventory.dto.SupplierForm;
import com.dienmay.inventory.service.SupplierService;
import com.dienmay.inventory.security.CurrentInventoryUser;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory/suppliers")
public class SupplierController {
    private final SupplierService suppliers;
    private final CurrentInventoryUser current;
    public SupplierController(SupplierService suppliers, CurrentInventoryUser current) { this.suppliers = suppliers; this.current = current; }
    @GetMapping
    public String list(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("suppliers", suppliers.search(keyword, com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,page), 10, Sort.by("name")), current.get()));
        model.addAttribute("keyword", keyword); return "inventory/suppliers/list";
    }
    @GetMapping("/new")
    public String add(Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", new SupplierForm()); }
        model.addAttribute("id", null); return "inventory/suppliers/form";
    }
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", suppliers.getForm(id, current.get())); }
        model.addAttribute("id", id); return "inventory/suppliers/form";
    }
    @PostMapping
    public String create(@Valid @ModelAttribute("form") SupplierForm form, BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { suppliers.create(form, current.get()); flash.addFlashAttribute("success", "Đã thêm nhà cung cấp."); return "redirect:/inventory/suppliers"; }
            catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return failure(form, errors, flash, "/new");
    }
    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") SupplierForm form, BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try { suppliers.update(id, form, current.get()); flash.addFlashAttribute("success", "Đã cập nhật nhà cung cấp."); return "redirect:/inventory/suppliers"; }
            catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return failure(form, errors, flash, "/" + id + "/edit");
    }
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        try { flash.addFlashAttribute("success", suppliers.delete(id, current.get())); }
        catch (BusinessException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/inventory/suppliers";
    }
    private String failure(SupplierForm form, BindingResult errors, RedirectAttributes flash, String suffix) {
        flash.addFlashAttribute("form", form); flash.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", errors);
        flash.addFlashAttribute("error", "Không thể lưu nhà cung cấp. Vui lòng kiểm tra thông tin.");
        return "redirect:/inventory/suppliers" + suffix;
    }
}
