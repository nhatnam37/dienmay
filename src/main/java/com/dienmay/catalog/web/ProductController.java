package com.dienmay.catalog.web;

import com.dienmay.catalog.dto.ProductView;
import com.dienmay.catalog.entity.ProductStatus;
import com.dienmay.catalog.service.*;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/catalog/products")
public class ProductController {
    private final ProductService products;
    private final ProductAdminService admin;
    private final CategoryService categories;
    private final BrandService brands;

    public ProductController(ProductService products, ProductAdminService admin,
                             CategoryService categories, BrandService brands) {
        this.products = products;
        this.admin = admin;
        this.categories = categories;
        this.brands = brands;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Long brandId,
                       @RequestParam(required = false) ProductStatus status,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("products", products.search(keyword, categoryId, brandId, status,
                PageRequest.of(Math.max(0, page), 10, Sort.by("id").descending())));
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brandId", brandId);
        model.addAttribute("status", status);
        choices(model);
        return "catalog/products/list";
    }

    @GetMapping("/new")
    public String add(Model model) {
        if (!model.containsAttribute("form")) { model.addAttribute("form", new ProductEditForm()); }
        return prepareForm(null, null, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        ProductView current = products.getById(id);
        if (!model.containsAttribute("form")) { model.addAttribute("form", ProductEditForm.from(current)); }
        return prepareForm(id, current.getImageUrl(), model);
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("product", products.getById(id));
        return "catalog/products/detail";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProductEditForm form, BindingResult errors,
                         RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                admin.create(form, form.getImage());
                flash.addFlashAttribute("success", "Đã thêm sản phẩm.");
                return "redirect:/admin/catalog/products";
            } catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return formError(form, errors, flash, "/new");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ProductEditForm form,
                         BindingResult errors, RedirectAttributes flash) {
        if (!errors.hasErrors()) {
            try {
                admin.update(id, form, form.getImage());
                flash.addFlashAttribute("success", "Đã cập nhật sản phẩm.");
                return "redirect:/admin/catalog/products";
            } catch (BusinessException exception) { errors.reject("business", exception.getMessage()); }
        }
        return formError(form, errors, flash, "/" + id + "/edit");
    }

    @PostMapping("/{id}/discontinue")
    public String discontinue(@PathVariable Long id, RedirectAttributes flash) {
        try {
            products.discontinue(id);
            flash.addFlashAttribute("success", "Đã ngừng kinh doanh sản phẩm.");
        } catch (BusinessException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/admin/catalog/products";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id, RedirectAttributes flash) {
        try {
            products.reactivate(id);
            flash.addFlashAttribute("success", "Đã kinh doanh lại sản phẩm.");
        } catch (BusinessException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        return "redirect:/admin/catalog/products";
    }

    private void choices(Model model) {
        model.addAttribute("categories", categories.list());
        model.addAttribute("brands", brands.list());
        model.addAttribute("statuses", ProductStatus.values());
    }

    private String prepareForm(Long id, String currentImage, Model model) {
        model.addAttribute("id", id);
        model.addAttribute("currentImage", currentImage);
        choices(model);
        return "catalog/products/form";
    }

    private String formError(ProductEditForm form, BindingResult errors, RedirectAttributes flash, String suffix) {
        // Không giữ nội dung MultipartFile trong session flash; trình duyệt cần chọn lại ảnh.
        form.setImage(null);
        form.setImageUrl(null);
        flash.addFlashAttribute("form", form);
        flash.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX + "form", errors);
        flash.addFlashAttribute("error", "Không thể lưu. Kiểm tra thông tin và chọn lại ảnh nếu có.");
        return "redirect:/admin/catalog/products" + suffix;
    }
}
