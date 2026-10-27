package com.dienmay.storefront.web;
import com.dienmay.storefront.cart.*;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.WebUtils;
@Controller public class CartController {
    public static final String CART_KEY="STOREFRONT_CART";
    private final CartService service;
    public CartController(CartService service){this.service=service;}
    public static CartState state(HttpSession session){synchronized(WebUtils.getSessionMutex(session)){CartState cart=(CartState)session.getAttribute(CART_KEY);if(cart==null){cart=new CartState();session.setAttribute(CART_KEY,cart);}return cart;}}
    @GetMapping("/cart") public String cart(HttpSession session,Model model){model.addAttribute("cart",service.view(state(session)));return "storefront/cart";}
    @PostMapping("/cart/items") public String add(@RequestParam Long productId,@RequestParam(defaultValue="1") int quantity,HttpSession session,RedirectAttributes flash){return mutate(()->service.add(state(session),productId,quantity),flash);}
    @PostMapping("/cart/items/{id}") public String change(@PathVariable Long id,@RequestParam int quantity,HttpSession session,RedirectAttributes flash){return mutate(()->service.change(state(session),id,quantity),flash);}
    @PostMapping("/cart/items/{id}/remove") public String remove(@PathVariable Long id,HttpSession session,RedirectAttributes flash){return mutate(()->service.remove(state(session),id),flash);}
    @PostMapping("/cart/clear") public String clear(HttpSession session,RedirectAttributes flash){return mutate(()->service.clear(state(session)),flash);}
    private String mutate(Runnable action,RedirectAttributes flash){try{action.run();flash.addFlashAttribute("success","Đã cập nhật giỏ hàng.");}catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}return "redirect:/cart";}
}
