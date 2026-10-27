package com.dienmay.storefront.web;
import com.dienmay.storefront.service.StorefrontService;
import com.dienmay.common.exception.BusinessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
@Controller public class StorefrontController {
    private final StorefrontService service;
    public StorefrontController(StorefrontService service){this.service=service;}
    @GetMapping("/") public String home(Model model){model.addAttribute("categories",service.categories());model.addAttribute("products",service.search(null,null,null,null,null,"newest",0).getContent());return "storefront/home";}
    @GetMapping({"/products","/search"}) public String products(@RequestParam(required=false) String keyword,@RequestParam(required=false) Long categoryId,@RequestParam(required=false) Long brandId,@RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,@RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="0") int page,Model model){
        try{model.addAttribute("page",service.search(keyword,categoryId,brandId,minPrice,maxPrice,sort,page));}catch(BusinessException error){model.addAttribute("error",error.getMessage());model.addAttribute("page",org.springframework.data.domain.Page.empty());}
        model.addAttribute("categories",service.categories());model.addAttribute("brands",service.brands());model.addAttribute("keyword",keyword);model.addAttribute("categoryId",categoryId);model.addAttribute("brandId",brandId);model.addAttribute("minPrice",minPrice);model.addAttribute("maxPrice",maxPrice);model.addAttribute("sort",sort);return "storefront/products";
    }
    @GetMapping("/products/{id}") public String detail(@PathVariable Long id,Model model){model.addAttribute("detail",service.detail(id));return "storefront/product";}
}
