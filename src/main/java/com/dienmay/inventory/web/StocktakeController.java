package com.dienmay.inventory.web;

import com.dienmay.common.exception.BusinessException;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.StocktakeStatus;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.service.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/inventory/stocktakes")
public class StocktakeController {
    private final StocktakeService takes;
    private final InventoryChoiceService choices;
    private final CurrentInventoryUser current;
    public StocktakeController(StocktakeService takes,InventoryChoiceService choices,CurrentInventoryUser current){this.takes=takes;this.choices=choices;this.current=current;}
    @GetMapping
    public String list(@RequestParam(required=false) Long branchId,@RequestParam(required=false) StocktakeStatus status,@RequestParam(defaultValue="0") int page,Model model){
        var actor=current.get();model.addAttribute("takes",takes.search(branchId,status,com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,page),10,Sort.by("id").descending()),actor));
        model.addAttribute("branchId",branchId);model.addAttribute("status",status);model.addAttribute("isAdmin",actor.hasRole("ADMIN"));model.addAttribute("branches",choices.branches(actor));return "inventory/stocktakes/list";
    }
    @GetMapping("/new")
    public String add(Model model){var actor=current.get();model.addAttribute("branches",choices.branches(actor));return "inventory/stocktakes/new";}
    @PostMapping
    public String open(@RequestParam(required=false) Long branchId,RedirectAttributes flash){
        try{Long id=takes.open(branchId,current.get());flash.addFlashAttribute("success","Đã mở phiếu và lưu mốc tồn. Hãy nhập kết quả đếm thực tế.");return "redirect:/inventory/stocktakes/"+id+"/count";}
        catch(BusinessException exception){flash.addFlashAttribute("error",exception.getMessage());}
        catch(DataAccessException exception){flash.addFlashAttribute("error","Chưa thể mở phiếu do dữ liệu thay đổi đồng thời. Vui lòng thử lại.");}
        return "redirect:/inventory/stocktakes/new";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,Model model){model.addAttribute("detail",takes.getById(id,current.get()));return "inventory/stocktakes/detail";}
    @GetMapping("/{id}/count")
    public String count(@PathVariable Long id,Model model,RedirectAttributes flash){
        try{var actor=current.get();if(!model.containsAttribute("form")){model.addAttribute("form",takes.getCountForm(id,actor));}
            model.addAttribute("detail",takes.getById(id,actor));model.addAttribute("products",takes.productChoices(actor));model.addAttribute("blankLine",new StocktakeCountLineForm());return "inventory/stocktakes/count";}
        catch(BusinessException exception){flash.addFlashAttribute("error",exception.getMessage());return "redirect:/inventory/stocktakes/"+id;}
    }
    @PostMapping("/{id}/count")
    public String save(@PathVariable Long id,@ModelAttribute("form") StocktakeCountForm form,BindingResult binding,RedirectAttributes flash){
        if(binding.hasErrors()){flash.addFlashAttribute("countErrors",List.of("Sản phẩm không hợp lệ. Vui lòng kiểm tra các dòng."));}
        else{try{takes.saveCount(id,form,current.get());flash.addFlashAttribute("success","Đã lưu kết quả đếm. Kiểm tra chênh lệch trước khi chốt.");return "redirect:/inventory/stocktakes/"+id;}
            catch(StocktakeValidationException exception){flash.addFlashAttribute("countErrors",exception.getErrors());}
            catch(BusinessException exception){flash.addFlashAttribute("countErrors",List.of(exception.getMessage()));}
            catch(DataAccessException exception){flash.addFlashAttribute("countErrors",List.of("Dữ liệu thay đổi đồng thời; phiếu chưa được lưu. Vui lòng đối chiếu lại."));}}
        flash.addFlashAttribute("form",form);return "redirect:/inventory/stocktakes/"+id+"/count";
    }
    @PostMapping("/{id}/reconcile")
    public String reconcile(@PathVariable Long id,RedirectAttributes flash){
        try{takes.reconcile(id,current.get());flash.addFlashAttribute("success","Đã lấy mốc tồn mới và xóa kết quả đếm cũ. Vui lòng đếm lại rồi lưu kết quả.");}
        catch(BusinessException exception){flash.addFlashAttribute("error",exception.getMessage());}
        catch(DataAccessException exception){flash.addFlashAttribute("error","Chưa thể đối chiếu do dữ liệu thay đổi đồng thời. Vui lòng thử lại.");}
        return "redirect:/inventory/stocktakes/"+id;
    }
    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id,RedirectAttributes flash){
        try{takes.close(id,current.get());flash.addFlashAttribute("success","Đã chốt kiểm kê. Serial lạ chỉ được ghi nhận trong báo cáo, không tự điều chỉnh.");}
        catch(BusinessException exception){flash.addFlashAttribute("error",exception.getMessage());}
        catch(DataAccessException exception){flash.addFlashAttribute("error","Không thể chốt do dữ liệu thay đổi đồng thời. Toàn bộ điều chỉnh đã rollback.");}
        return "redirect:/inventory/stocktakes/"+id;
    }
}
