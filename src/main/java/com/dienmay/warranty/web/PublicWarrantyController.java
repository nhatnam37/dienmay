package com.dienmay.warranty.web;
import com.dienmay.warranty.service.PublicWarrantyLookupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Controller @RequestMapping("/warranty")
public class PublicWarrantyController {
    public static final String NOT_FOUND="Không tìm thấy thông tin bảo hành cho Serial này, vui lòng kiểm tra lại hoặc liên hệ cửa hàng";
    private final PublicWarrantyLookupService lookup;
    public PublicWarrantyController(PublicWarrantyLookupService lookup){this.lookup=lookup;}
    @ModelAttribute void privateResponse(HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");}
    @GetMapping public String form(){return "warranty/public/lookup";}
    @GetMapping("/track") public String trackForm(){return "warranty/public/track";}
    // POST render trực tiếp: không đưa Serial/mã phiếu lên URL, access log chuẩn hay flash/session.
    // Refresh có thể hỏi gửi lại form; đổi lại kết quả không tạo URL chia sẻ và không lưu dữ liệu tra trong session.
    @PostMapping public String lookup(@RequestParam(defaultValue="") String serialNo,HttpServletRequest request,Model model){
        var result=lookup.publicLookup(serialNo,request.getRemoteAddr());model.addAttribute("result",result.orElse(null));model.addAttribute("searched",true);model.addAttribute("notFoundMessage",NOT_FOUND);return "warranty/public/lookup";
    }
    @PostMapping("/track") public String track(@RequestParam(defaultValue="") String ticketCode,@RequestParam(defaultValue="") String serialNo,HttpServletRequest request,Model model){
        var result=lookup.publicTrack(ticketCode,serialNo,request.getRemoteAddr());model.addAttribute("result",result.orElse(null));model.addAttribute("searched",true);return "warranty/public/track";
    }
    @ExceptionHandler(ResponseStatusException.class) public String limited(ResponseStatusException error,HttpServletResponse response){
        if(error.getStatusCode().value()!=429)throw error;response.setStatus(429);response.setHeader("Retry-After","60");return "warranty/public/429";
    }
}
