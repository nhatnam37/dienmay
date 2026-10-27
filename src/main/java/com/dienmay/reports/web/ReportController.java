package com.dienmay.reports.web;
import com.dienmay.reports.*;
import com.dienmay.reports.ReportDtos.*;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.data.domain.PageRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.*;
import java.util.Set;
@Controller @RequestMapping("/reports")
public class ReportController {
    private final ReportQueryService reports;private final ReportScreenService screens;private final ReportProperties properties;private final Clock clock;
    public ReportController(ReportQueryService reports,ReportScreenService screens,ReportProperties properties,Clock clock){this.reports=reports;this.screens=screens;this.properties=properties;this.clock=clock;}
    @ModelAttribute("serialStatuses") public java.util.Map<String,com.dienmay.inventory.serial.SerialStatus> serialStatuses(){return java.util.Arrays.stream(com.dienmay.inventory.serial.SerialStatus.values()).collect(java.util.stream.Collectors.toMap(Enum::name,v->v));}
    @GetMapping public String index(){return "redirect:/reports/inventory";}
    @GetMapping("/{type:dashboard|revenue|products|inventory|low-stock}")
    public String page(@PathVariable String type,@ModelAttribute("filter") ReportFilter filter,BindingResult errors,@AuthenticationPrincipal AppUserDetails principal,Model model,HttpServletResponse response){
        var actor=SalesActor.from(principal);model.addAttribute("type",type);model.addAttribute("title",switch(type){case "dashboard"->"Tổng quan";case "revenue"->"Doanh thu";case "products"->"Sản phẩm bán chạy";case "inventory"->"Báo cáo tồn kho";default->"Hàng sắp hết";});
        boolean financial=!Set.of("inventory","low-stock").contains(type);model.addAttribute("meta",screens.meta(filter.scope(),financial,actor));
        try{
            if(errors.hasErrors())throw new BusinessException("Bộ lọc không hợp lệ. Vui lòng kiểm tra ngày và các giá trị đã chọn.");
            ReportRange range=ReportRange.resolve(filter.range(),clock);filter.setFrom(range.from());filter.setTo(range.to());
            switch(type){
                case "dashboard" -> {var dashboard=reports.dashboard(filter.scope(),actor);model.addAttribute("dashboard",dashboard);filter.setFrom(dashboard.last30Days().range().from());filter.setTo(dashboard.last30Days().range().to());}
                case "revenue" -> {model.addAttribute("rows",reports.revenueSeries(range,filter.getGranularity(),filter.scope(),actor));model.addAttribute("summary",reports.summary(range,filter.scope(),actor));}
                case "products" -> model.addAttribute("rows",reports.topProducts(range,filter.scope(),50,filter.getOrderBy(),actor));
                case "inventory" -> model.addAttribute("result",reports.inventoryStock(filter.scope(),filter.getCategoryId(),filter.getKeyword(),com.dienmay.common.pagination.PageRequestFactory.of(Math.max(0,filter.getPage()),20),actor));
                case "low-stock" -> {model.addAttribute("threshold",properties.getLowStockThreshold());model.addAttribute("rows",reports.lowStock(filter.scope(),null,actor));}
            }
        }catch(BusinessException error){model.addAttribute("error",error.getMessage());response.setStatus(400);}
        return "reports/page";
    }
}
