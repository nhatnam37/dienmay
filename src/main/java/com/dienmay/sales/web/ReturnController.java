package com.dienmay.sales.web;

import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.dto.*;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.sales.service.*;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/pos")
public class ReturnController {
    private final ReturnService service;private final ReturnQueryService queries;private final PosQueryService pos;
    public ReturnController(ReturnService service,ReturnQueryService queries,PosQueryService pos){this.service=service;this.queries=queries;this.pos=pos;}
    @GetMapping("/invoices/{id}/returns/new")
    public String form(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){var screen=queries.screen(id,SalesActor.from(user));model.addAttribute("screen",screen);ReturnForm submitted=(ReturnForm)model.getAttribute("form");ReturnForm form=new ReturnForm();if(submitted!=null)form.setReason(submitted.getReason());
        java.util.Map<Long,Integer> quantities=new java.util.HashMap<>();if(submitted!=null&&submitted.getLines()!=null)for(var line:submitted.getLines())if(line!=null&&line.getInvoiceItemId()!=null&&line.getQuantity()!=null)quantities.put(line.getInvoiceItemId(),line.getQuantity());
        for(var line:screen.lines()){ReturnForm.Line field=new ReturnForm.Line();field.setInvoiceItemId(line.invoiceItemId());field.setExpectedReturnedQuantity(line.returnedQuantity());field.setQuantity(line.returnable()?quantities.getOrDefault(line.invoiceItemId(),0):0);form.getLines().add(field);}model.addAttribute("form",form);return "pos/returns/form";}
    @PostMapping("/invoices/{id}/returns")
    public String submit(@PathVariable Long id,@Valid @ModelAttribute("form") ReturnForm form,BindingResult result,@AuthenticationPrincipal AppUserDetails user,RedirectAttributes flash){
        try{if(!result.hasErrors()){Long doc=service.returnItems(id,form,SalesActor.from(user));flash.addFlashAttribute("success","Đã ghi nhận trả hàng và hoàn tiền.");return "redirect:/pos/returns/"+doc;}flash.addFlashAttribute("error",result.getAllErrors().stream().map(e->e.getDefaultMessage()).distinct().collect(java.util.stream.Collectors.joining("; ")));}
        catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}flash.addFlashAttribute("form",form);return "redirect:/pos/invoices/"+id+"/returns/new";
    }
    @PostMapping("/invoices/{id}/cancel")
    public String cancel(@PathVariable Long id,@RequestParam String reason,@AuthenticationPrincipal AppUserDetails user,RedirectAttributes flash){try{Long doc=service.cancelInvoice(id,reason,SalesActor.from(user));flash.addFlashAttribute("success","Đã hủy hóa đơn và ghi nhận hoàn tiền.");return "redirect:/pos/returns/"+doc;}catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());ReturnForm form=new ReturnForm();form.setReason(reason);flash.addFlashAttribute("form",form);return "redirect:/pos/invoices/"+id+"/returns/new";}}
    @GetMapping("/returns")
    public String list(@RequestParam(required=false) Long branchId,@RequestParam(defaultValue="0") int page,@AuthenticationPrincipal AppUserDetails user,Model model){var actor=SalesActor.from(user);model.addAttribute("page",queries.list(branchId,page,actor));model.addAttribute("branches",pos.branchChoices(actor));model.addAttribute("branchId",branchId==null?actor.branchId():branchId);return "pos/returns/list";}
    @GetMapping("/returns/{id}")
    public String detail(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){model.addAttribute("detail",queries.detail(id,SalesActor.from(user)));return "pos/returns/detail";}
}
