package com.dienmay.orders.web;
import com.dienmay.orders.service.*;
import com.dienmay.orders.entity.*;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.validation.BindingResult;
import java.time.*;
import java.math.BigDecimal;
@Controller @RequestMapping("/staff/orders")
public class StaffOrderController {
    private final StaffOrderService service;private final StaffOrderQueryService queries;
    public StaffOrderController(StaffOrderService service,StaffOrderQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping public String list(@RequestParam(required=false) Long branchId,@RequestParam(required=false) OrderStatus status,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String code,@RequestParam(required=false) String phone,@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal AppUserDetails user,Model model){var actor=SalesActor.from(user);try{model.addAttribute("result",queries.search(branchId,status,from,to,code,phone,page,actor));}catch(BusinessException error){model.addAttribute("error",error.getMessage());model.addAttribute("result",Page.empty());}model.addAttribute("branches",queries.branches(actor));model.addAttribute("branchId",branchId);model.addAttribute("status",status);model.addAttribute("from",from);model.addAttribute("to",to);model.addAttribute("code",code);model.addAttribute("phone",phone);return "orders/staff/list";}
    @GetMapping("/{id}") public String detail(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){var screen=queries.detail(id,SalesActor.from(user));model.addAttribute("screen",screen);return "orders/staff/detail";}
    public static class Action {
        private Long version;private OrderStatus target;private String reason;private BigDecimal cashReceived;
        public Long getVersion(){return version;}public void setVersion(Long v){version=v;}
        public OrderStatus getTarget(){return target;}public void setTarget(OrderStatus v){target=v;}
        public String getReason(){return reason;}public void setReason(String v){reason=v;}
        public BigDecimal getCashReceived(){return cashReceived;}public void setCashReceived(BigDecimal v){cashReceived=v;}
    }
    @PostMapping("/{id}/transition") public String change(@PathVariable Long id,@ModelAttribute Action action,BindingResult errors,@AuthenticationPrincipal AppUserDetails user,RedirectAttributes flash){
        if(errors.hasErrors())flash.addFlashAttribute("error","Thông tin thao tác hoặc số tiền không hợp lệ.");
        else try{Long invoice=service.change(id,action.version,action.target,action.reason,action.cashReceived,SalesActor.from(user));flash.addFlashAttribute("success",invoice==null?"Đã cập nhật đơn hàng.":"Đã hoàn tất đơn và tạo hóa đơn.");}
        catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}
        catch(OptimisticLockingFailureException error){flash.addFlashAttribute("error","Đơn đã được cập nhật, tải lại.");}
        return "redirect:/staff/orders/"+id;
    }
}
