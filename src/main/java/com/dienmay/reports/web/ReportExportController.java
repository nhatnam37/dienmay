package com.dienmay.reports.web;
import com.dienmay.reports.*;
import com.dienmay.auth.AppUserDetails;
import com.dienmay.sales.security.SalesActor;
import com.dienmay.common.exception.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import java.nio.file.Files;
@Controller @RequestMapping("/reports/export")
public class ReportExportController {
    private final ReportExcelService exports;
    public ReportExportController(ReportExcelService exports){this.exports=exports;}
    @GetMapping("/{type:revenue|products|inventory}") public void download(@PathVariable String type,@ModelAttribute ReportFilter f,BindingResult errors,@AuthenticationPrincipal AppUserDetails user,HttpServletResponse response)throws java.io.IOException{
        if(errors.hasErrors())throw new BusinessException("Bộ lọc xuất Excel không hợp lệ.");
        // Hoàn tất workbook và kiểm tra giới hạn trước khi gửi header/body: không trả tệp xlsx dở khi bị từ chối.
        try(var file=exports.export(type,f.range(),f.scope(),f.getGranularity(),f.getOrderBy(),f.getCategoryId(),f.getKeyword(),SalesActor.from(user))){
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(file.filename()).build().toString());
            response.setHeader("Cache-Control","no-store");response.setContentLengthLong(Files.size(file.path()));Files.copy(file.path(),response.getOutputStream());
        }
    }
    @ExceptionHandler(BusinessException.class) @ResponseBody public ResponseEntity<?> invalid(BusinessException e){return ResponseEntity.badRequest().body(java.util.Map.of("message",e.getMessage()));}
}
