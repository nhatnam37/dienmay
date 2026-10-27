package com.dienmay.warranty.web;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.warranty.service.WarrantyScreenService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@Controller
public class CustomerWarrantyController {
    private final WarrantyScreenService queries;
    public CustomerWarrantyController(WarrantyScreenService queries){this.queries=queries;}
    @GetMapping("/account/warranties") public String list(@AuthenticationPrincipal CustomerUserDetails user,Model model){
        model.addAttribute("profiles",queries.customerProfiles(user.getCustomerId()));return "warranty/customer/list";
    }
}
