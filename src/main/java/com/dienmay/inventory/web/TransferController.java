package com.dienmay.inventory.web;

import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.TransferStatus;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.service.TransferService;
import com.dienmay.common.exception.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/inventory/transfers")
public class TransferController {
    private final TransferService transfers;
    private final CurrentInventoryUser current;
    public TransferController(TransferService transfers, CurrentInventoryUser current) { this.transfers = transfers; this.current = current; }
    @GetMapping
    public String list(@RequestParam(required = false) TransferStatus status, @RequestParam(defaultValue = "ALL") String direction,
            @RequestParam(required = false) Long branchId, @RequestParam(defaultValue = "0") int page, Model model) {
        InventoryActor actor = current.get();
        var pageable = com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,page), 10, Sort.by("id").descending());
        try { model.addAttribute("transfers", transfers.search(status, direction, branchId, pageable, actor)); }
        catch (BusinessException exception) { model.addAttribute("error", exception.getMessage()); model.addAttribute("transfers", Page.empty(pageable)); }
        model.addAttribute("status", status); model.addAttribute("direction", direction); model.addAttribute("branchId", branchId);
        common(model, actor); return "inventory/transfers/list";
    }
    @GetMapping("/new")
    public String add(Model model) {
        InventoryActor actor = current.get();
        if (!model.containsAttribute("form")) { TransferForm form = new TransferForm(); form.setFromBranchId(actor.branchId()); model.addAttribute("form", form); }
        common(model, actor); model.addAttribute("products", transfers.quantityChoices(actor));
        model.addAttribute("blankLine", new TransferQuantityForm()); return "inventory/transfers/form";
    }
    @PostMapping
    public String create(@ModelAttribute("form") TransferForm form, BindingResult binding, RedirectAttributes flash) {
        if (binding.hasErrors()) { flash.addFlashAttribute("transferErrors", List.of("Chi nhánh hoặc sản phẩm không hợp lệ. Vui lòng kiểm tra các dòng.")); }
        else {
            try { Long id = transfers.createDraft(form, current.get()); flash.addFlashAttribute("success", "Đã lưu phiếu nháp. Hàng chưa được giữ hoặc xuất kho."); return "redirect:/inventory/transfers/" + id; }
            catch (TransferValidationException exception) { flash.addFlashAttribute("transferErrors", exception.getErrors()); }
            catch (BusinessException exception) { flash.addFlashAttribute("transferErrors", List.of(exception.getMessage())); }
            catch (DataAccessException exception) { flash.addFlashAttribute("transferErrors", List.of("Không thể lưu do dữ liệu thay đổi đồng thời. Vui lòng kiểm tra và thử lại.")); }
        }
        flash.addFlashAttribute("form", form); return "redirect:/inventory/transfers/new";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) { model.addAttribute("detail", transfers.getById(id, current.get())); return "inventory/transfers/detail"; }
    @PostMapping("/{id}/ship")
    public String ship(@PathVariable Long id, RedirectAttributes flash) { return action(id, "ship", flash); }
    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id, RedirectAttributes flash) { return action(id, "receive", flash); }
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes flash) { return action(id, "cancel", flash); }
    private String action(Long id, String action, RedirectAttributes flash) {
        try {
            InventoryActor actor = current.get();
            switch (action) {
                case "ship" -> transfers.ship(id, actor);
                case "receive" -> transfers.receive(id, actor);
                case "cancel" -> transfers.cancel(id, actor);
                default -> throw new IllegalArgumentException("Unknown action");
            }
            flash.addFlashAttribute("success", switch (action) { case "ship" -> "Đã xuất kho toàn bộ phiếu."; case "receive" -> "Đã nhận đủ hàng."; default -> "Đã hủy phiếu nháp."; });
        } catch (BusinessException exception) { flash.addFlashAttribute("error", exception.getMessage()); }
        catch (DataAccessException exception) { flash.addFlashAttribute("error", "Dữ liệu đang thay đổi đồng thời. Thao tác đã rollback; vui lòng tải lại phiếu và thử lại."); }
        return "redirect:/inventory/transfers/" + id;
    }
    private void common(Model model, InventoryActor actor) {
        model.addAttribute("actor", actor); model.addAttribute("isAdmin", actor.hasRole("ADMIN"));
        model.addAttribute("branches", transfers.branchChoices(actor)); model.addAttribute("statuses", TransferStatus.values());
    }
}
