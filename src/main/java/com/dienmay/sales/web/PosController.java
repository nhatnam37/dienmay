package com.dienmay.sales.web;

import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.config.PosProperties;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.entity.InvoiceStatus;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.*;
import com.dienmay.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/pos")
public class PosController {
    private final ReturnQueryService returns;private final CheckoutService checkout;private final SerialLookupForSale serials;private final PosQueryService queries;private final PosProperties properties;private final ObjectMapper json;
    public PosController(ReturnQueryService returns,CheckoutService checkout,SerialLookupForSale serials,PosQueryService queries,PosProperties properties,ObjectMapper json){this.returns=returns;this.checkout=checkout;this.serials=serials;this.queries=queries;this.properties=properties;this.json=json;}
    @GetMapping
    public String counter(@AuthenticationPrincipal AppUserDetails user,Model model){
        var actor=SalesActor.from(user);model.addAttribute("branchName",queries.branchName(actor));model.addAttribute("branchId",actor.branchId());model.addAttribute("employeeId",actor.employeeId());model.addAttribute("checkoutToken",UUID.randomUUID().toString());model.addAttribute("discountLimit",actor.hasRole("CASHIER")?properties.getMaxCashierDiscountPercent():100);return "pos/counter";
    }
    @GetMapping("/serials") @ResponseBody
    public SaleSerialView serial(@RequestParam String serialNo,@AuthenticationPrincipal AppUserDetails user){var actor=SalesActor.from(user);return serials.lookup(serialNo,actor.branchId(),actor);}
    @GetMapping("/accessories") @ResponseBody
    public List<AccessoryView> accessories(@RequestParam(defaultValue="") String keyword,@AuthenticationPrincipal AppUserDetails user){return queries.accessories(keyword,SalesActor.from(user));}
    @PostMapping("/checkout")
    public String checkout(@RequestParam String payload,@AuthenticationPrincipal AppUserDetails user,RedirectAttributes flash){
        try{
            CheckoutRequest input=json.readValue(payload,CheckoutRequest.class);if(input==null)throw new BusinessException("Thiếu nội dung thanh toán.");
            // Chi nhánh/actor lấy từ principal; bỏ qua branchId do trình duyệt gửi lên.
            var actor=SalesActor.from(user);var request=new CheckoutRequest(input.checkoutToken(),actor.branchId(),input.customerName(),input.customerPhone(),input.discountAmount(),input.note(),input.items(),input.payments());
            InvoiceView invoice=checkout.checkout(request,actor);flash.addFlashAttribute("completedToken",invoice.checkoutToken());return "redirect:/pos/invoices/"+invoice.id();
        }catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}
        catch(JsonProcessingException error){flash.addFlashAttribute("error","Dữ liệu thanh toán không hợp lệ. Vui lòng kiểm tra lại giỏ hàng.");}
        return "redirect:/pos";
    }
    @GetMapping("/invoices")
    public String invoices(@RequestParam(required=false) Long branchId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String code,@RequestParam(required=false) String phone,@RequestParam(required=false) InvoiceStatus status,@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal AppUserDetails user,Model model){
        var actor=SalesActor.from(user);model.addAttribute("branches",queries.branchChoices(actor));
        try{model.addAttribute("page",queries.search(branchId,from,to,code,phone,status,page,actor));}
        catch(BusinessException error){model.addAttribute("error",error.getMessage());model.addAttribute("page",org.springframework.data.domain.Page.empty());}
        @SuppressWarnings("unchecked") var rows=(org.springframework.data.domain.Page<InvoiceView>)model.getAttribute("page");model.addAttribute("returnLabels",returns.labels(rows.getContent().stream().map(InvoiceView::id).toList()));
        model.addAttribute("branchId",branchId);model.addAttribute("from",from);model.addAttribute("to",to);model.addAttribute("code",code);model.addAttribute("phone",phone);model.addAttribute("filterStatus",status);return "pos/invoices";
    }
    @GetMapping("/invoices/{id}")
    public String invoice(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){var actor=SalesActor.from(user);model.addAttribute("detail",queries.detail(id,actor));model.addAttribute("returnLabels",returns.labels(java.util.List.of(id)));if(actor.hasRole("ADMIN")||actor.hasRole("BRANCH_MANAGER"))model.addAttribute("returnScreen",returns.screen(id,actor));model.addAttribute("storageOwner",actor.employeeId()+":"+actor.branchId());return "pos/invoice";}
}
