package com.dienmay.integrity;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/admin/integrity")
public class IntegrityController {
    private final DataIntegrityAuditService audits;
    public IntegrityController(DataIntegrityAuditService audits){this.audits=audits;}
    @GetMapping public String page(){return "integrity/index";}
    // POST có CSRF nhưng chỉ chẩn đoán SELECT; không sửa dữ liệu khách hàng.
    @PostMapping public String audit(Model model){
        var report=audits.audit();model.addAttribute("checks",report.checks());model.addAttribute("report",report);return "integrity/index";
    }
}
