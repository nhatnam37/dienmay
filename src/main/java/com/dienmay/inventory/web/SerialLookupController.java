package com.dienmay.inventory.web;

import com.dienmay.inventory.security.CurrentInventoryUser;
import com.dienmay.inventory.service.SerialLookupService;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class SerialLookupController {
    private final SerialLookupService lookup;
    private final CurrentInventoryUser current;
    public SerialLookupController(SerialLookupService lookup, CurrentInventoryUser current) { this.lookup = lookup; this.current = current; }
    @GetMapping("/inventory/serials")
    public String search(@RequestParam(required = false) String serialNo, Model model) {
        model.addAttribute("serialNo", serialNo);
        if (serialNo != null && !serialNo.isBlank()) {
            try { model.addAttribute("serial", lookup.lookup(serialNo, current.get())); }
            catch (ResourceNotFoundException | BusinessException exception) { model.addAttribute("error", exception.getMessage()); }
        }
        return "inventory/serials/lookup";
    }
}
