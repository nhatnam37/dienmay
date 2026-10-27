package com.dienmay.reports.web;
import com.dienmay.reports.*;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.security.SalesActor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.http.*;
import com.dienmay.common.exception.*;
@RestController @RequestMapping("/reports/api")
public class ReportApiController {
    private final ReportQueryService reports;
    public ReportApiController(ReportQueryService reports){this.reports=reports;}
    @GetMapping("/{type:revenue-series|by-channel|top-products|by-branch}") public Object data(@PathVariable String type,@ModelAttribute ReportFilter f,BindingResult errors,@AuthenticationPrincipal AppUserDetails user){
        if(errors.hasErrors())throw new BusinessException("Bộ lọc báo cáo không hợp lệ.");var actor=SalesActor.from(user);
        return switch(type){case "revenue-series"->reports.revenueSeries(f.range(),f.getGranularity(),f.scope(),actor);case "by-channel"->reports.revenueByChannel(f.range(),f.scope(),actor);case "top-products"->reports.topProducts(f.range(),f.scope(),5,f.getOrderBy(),actor);default->reports.revenueByBranch(f.range(),actor);};
    }
    @ExceptionHandler(BusinessException.class) public ResponseEntity<?> invalid(BusinessException error){return ResponseEntity.badRequest().body(java.util.Map.of("message",error.getMessage()));}
    @ExceptionHandler(ResourceNotFoundException.class) public ResponseEntity<?> missing(ResourceNotFoundException error){return ResponseEntity.status(404).body(java.util.Map.of("message",error.getMessage()));}
}
