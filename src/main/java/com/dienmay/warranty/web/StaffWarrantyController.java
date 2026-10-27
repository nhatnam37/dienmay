package com.dienmay.warranty.web;
import com.dienmay.warranty.service.*;
import com.dienmay.warranty.entity.WarrantyTicketStatus;
import com.dienmay.warranty.dto.ReceiveRequest;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
@Controller @RequestMapping("/staff/warranty")
public class StaffWarrantyController {
    private final WarrantyScreenService queries;private final WarrantyTicketService service;
    public StaffWarrantyController(WarrantyScreenService queries,WarrantyTicketService service){this.queries=queries;this.service=service;}
    @ModelAttribute("statuses") public WarrantyTicketStatus[] statuses(){return WarrantyTicketStatus.values();}
    @GetMapping("/lookup") public String lookupForm(){return "warranty/staff/lookup";}
    @PostMapping("/lookup") public String lookup(@RequestParam(defaultValue="") String serialNo,@RequestParam(defaultValue="") String phone,@AuthenticationPrincipal AppUserDetails user,Model model){
        try{model.addAttribute("results",queries.lookup(serialNo,phone,SalesActor.from(user)));model.addAttribute("searched",true);}
        catch(BusinessException error){model.addAttribute("error",error.getMessage());}return "warranty/staff/lookup";
    }
    @GetMapping({"","/tickets"}) public String list(@RequestParam(required=false) Long branchId,@RequestParam(required=false) WarrantyTicketStatus status,
            @RequestParam(required=false) String code,@RequestParam(required=false) String serialNo,@RequestParam(required=false) String phone,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="0") int page,@AuthenticationPrincipal AppUserDetails user,Model model){
        var actor=SalesActor.from(user);try{model.addAttribute("result",queries.search(branchId,status,code,serialNo,phone,from,to,page,actor));}
        catch(BusinessException error){model.addAttribute("error",error.getMessage());model.addAttribute("result",Page.empty());}
        model.addAttribute("branches",queries.branches(actor));model.addAttribute("branchId",branchId);model.addAttribute("status",status);
        model.addAttribute("code",code);model.addAttribute("serialNo",serialNo);model.addAttribute("phone",phone);model.addAttribute("from",from);model.addAttribute("to",to);return "warranty/staff/list";
    }
    @GetMapping("/tickets/new") public String newTicket(@RequestParam(required=false) String serialNo,@AuthenticationPrincipal AppUserDetails user,Model model){
        WarrantyReceiveForm form=model.containsAttribute("receiveForm")?(WarrantyReceiveForm)model.getAttribute("receiveForm"):new WarrantyReceiveForm();
        if(form.getSerialNo()==null)form.setSerialNo(serialNo);model.addAttribute("receiveForm",form);
        if(form.getSerialNo()!=null&&!form.getSerialNo().isBlank())try{var intake=queries.intake(form.getSerialNo(),SalesActor.from(user));model.addAttribute("intake",intake);form.setWarrantyId(intake.warrantyId());}
        catch(BusinessException|ResourceNotFoundException error){model.addAttribute("error",error.getMessage());}
        Object validation=model.getAttribute(BindingResult.MODEL_KEY_PREFIX+"receiveForm");
        if(validation instanceof BindingResult errors && errors.hasErrors())model.addAttribute("error",errors.getAllErrors().stream().map(e->e.getDefaultMessage()).collect(java.util.stream.Collectors.joining("; ")));
        return "warranty/staff/new";
    }
    @PostMapping("/tickets") public String receive(@Valid @ModelAttribute("receiveForm") WarrantyReceiveForm form,BindingResult errors,@AuthenticationPrincipal AppUserDetails user,RedirectAttributes flash){
        if(!errors.hasErrors())try{var ticket=service.receive(new ReceiveRequest(form.getWarrantyId(),form.getSerialNo(),form.getIssueDescription(),form.getOverrideReason()),SalesActor.from(user));flash.addFlashAttribute("success","Đã tiếp nhận bảo hành.");return "redirect:/staff/warranty/tickets/"+ticket.id();}
        catch(BusinessException|ResourceNotFoundException error){errors.reject("business",error.getMessage());}
        flash.addFlashAttribute("receiveForm",form);flash.addFlashAttribute(BindingResult.MODEL_KEY_PREFIX+"receiveForm",errors);return "redirect:/staff/warranty/tickets/new";
    }
    @GetMapping("/tickets/{id}") public String detail(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){model.addAttribute("screen",queries.detail(id,SalesActor.from(user)));return "warranty/staff/detail";}
    @GetMapping("/tickets/{id}/print") public String print(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){model.addAttribute("screen",queries.detail(id,SalesActor.from(user)));return "warranty/staff/print";}
    @GetMapping("/invoices/{id}") public String invoice(@PathVariable Long id,@AuthenticationPrincipal AppUserDetails user,Model model){model.addAttribute("invoice",queries.invoice(id,SalesActor.from(user)));return "warranty/staff/invoice";}
    public static class Action {
        @NotNull private Long version;@NotNull private WarrantyTicketStatus target;
        @Size(max=1000) private String reason;@Size(max=1000) private String diagnosis;@Size(max=1000) private String resolution;
        public Long getVersion(){return version;}public void setVersion(Long v){version=v;}
        public WarrantyTicketStatus getTarget(){return target;}public void setTarget(WarrantyTicketStatus v){target=v;}
        public String getReason(){return reason;}public void setReason(String v){reason=v;}
        public String getDiagnosis(){return diagnosis;}public void setDiagnosis(String v){diagnosis=v;}
        public String getResolution(){return resolution;}public void setResolution(String v){resolution=v;}
    }
    @PostMapping("/tickets/{id}/transition") public String change(@PathVariable Long id,@Valid @ModelAttribute Action form,BindingResult errors,@AuthenticationPrincipal AppUserDetails user,RedirectAttributes flash){
        if(errors.hasErrors())flash.addFlashAttribute("error","Thông tin thao tác không hợp lệ; nội dung tối đa 1000 ký tự.");
        else try{var actor=SalesActor.from(user);switch(form.target){
            case IN_REPAIR -> service.startRepair(id,form.version,actor);
            case COMPLETED -> service.complete(id,form.version,form.diagnosis,form.resolution,actor);
            case REJECTED -> service.reject(id,form.version,form.reason,actor);
            case RETURNED -> service.returnToCustomer(id,form.version,actor);
            case CANCELLED -> service.cancel(id,form.version,form.reason,actor);
            default -> throw new BusinessException("Thao tác chuyển trạng thái không hợp lệ.");
        }flash.addFlashAttribute("success","Đã cập nhật phiếu bảo hành.");}
        catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}
        catch(OptimisticLockingFailureException error){flash.addFlashAttribute("error","Phiếu đã được cập nhật, tải lại.");}
        return "redirect:/staff/warranty/tickets/"+id;
    }
}
