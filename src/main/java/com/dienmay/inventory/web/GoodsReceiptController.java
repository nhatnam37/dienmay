package com.dienmay.inventory.web;

import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.service.*;
import com.dienmay.inventory.security.*;
import com.dienmay.common.exception.BusinessException;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/inventory/receipts")
public class GoodsReceiptController {
    private final GoodsReceiptService receipts;
    private final SupplierService suppliers;
    private final InventoryChoiceService choices;
    private final CurrentInventoryUser current;
    public GoodsReceiptController(GoodsReceiptService receipts, SupplierService suppliers, InventoryChoiceService choices, CurrentInventoryUser current) {
        this.receipts = receipts; this.suppliers = suppliers; this.choices = choices; this.current = current;
    }
    @GetMapping
    public String list(@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, Model model) {
        InventoryActor actor = current.get();
        var pageable = PageRequest.of(Math.max(0,page),10,Sort.by("id").descending());
        try { model.addAttribute("receipts", receipts.search(branchId, supplierId, from, to, pageable, actor)); }
        catch (BusinessException exception) { model.addAttribute("error", exception.getMessage()); model.addAttribute("receipts", Page.empty(pageable)); }
        model.addAttribute("branchId", branchId); model.addAttribute("supplierId", supplierId);
        model.addAttribute("from", from); model.addAttribute("to", to); common(model, actor, false);
        return "inventory/receipts/list";
    }
    @GetMapping("/new")
    public String add(Model model) {
        InventoryActor actor = current.get();
        if (!model.containsAttribute("form")) { GoodsReceiptForm form = new GoodsReceiptForm(); form.setBranchId(actor.branchId()); model.addAttribute("form", form); }
        model.addAttribute("blankLine", new ReceiptLineForm());
        model.addAttribute("products", choices.products(actor)); common(model, actor, true);
        return "inventory/receipts/form";
    }
    @PostMapping
    public String create(@ModelAttribute("form") GoodsReceiptForm form, BindingResult binding, RedirectAttributes flash) {
        // Raw quantity/unitCost được service kiểm tra cùng Serial để gom hết lỗi các dòng.
        if (binding.hasErrors()) {
            flash.addFlashAttribute("receiptErrors", binding.getFieldErrors().stream().map(error -> "Thông tin " + error.getField() + " không hợp lệ.").toList());
        } else {
            try {
                Long id = receipts.create(form, current.get());
                flash.addFlashAttribute("success", "Đã nhập kho thành công.");
                return "redirect:/inventory/receipts/" + id;
            } catch (ReceiptValidationException exception) { flash.addFlashAttribute("receiptErrors", exception.getErrors()); }
            catch (BusinessException exception) { flash.addFlashAttribute("receiptErrors", List.of(exception.getMessage())); }
            catch (org.springframework.dao.DataAccessException exception) {
                flash.addFlashAttribute("receiptErrors", List.of("Không thể lưu do dữ liệu thay đổi đồng thời. Phiếu đã rollback; vui lòng kiểm tra và thử lại."));
            }
        }
        flash.addFlashAttribute("form", form);
        flash.addFlashAttribute("error", "Phiếu chưa được lưu. Vui lòng sửa tất cả lỗi bên dưới.");
        return "redirect:/inventory/receipts/new";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("detail", receipts.getById(id, current.get())); return "inventory/receipts/detail";
    }
    private void common(Model model, InventoryActor actor, boolean activeOnly) {
        model.addAttribute("isAdmin", actor.hasRole("ADMIN")); model.addAttribute("actor", actor);
        model.addAttribute("branches", choices.branches(actor)); model.addAttribute("suppliers", suppliers.choices(activeOnly, actor));
    }
}
