package com.dienmay.inventory.web;

import com.dienmay.inventory.service.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.SerialStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/inventory/stock")
public class InventoryStockController {
    private final StockQueryService stock;
    private final InventoryChoiceService choices;
    private final CurrentInventoryUser current;
    public InventoryStockController(StockQueryService stock, InventoryChoiceService choices, CurrentInventoryUser current) {
        this.stock = stock; this.choices = choices; this.current = current;
    }
    @GetMapping
    public String list(@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String keyword, Model model) {
        InventoryActor actor = current.get();
        model.addAttribute("rows", stock.search(branchId, categoryId, keyword, actor));
        model.addAttribute("isAdmin", actor.hasRole("ADMIN")); model.addAttribute("branches", choices.branches(actor));
        model.addAttribute("categories", choices.categories(actor)); model.addAttribute("statuses", SerialStatus.values());
        model.addAttribute("branchId", branchId); model.addAttribute("categoryId", categoryId); model.addAttribute("keyword", keyword);
        return "inventory/stock";
    }
}
