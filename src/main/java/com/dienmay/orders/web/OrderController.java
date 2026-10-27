package com.dienmay.orders.web;
import com.dienmay.orders.dto.*;
import com.dienmay.orders.service.*;
import com.dienmay.orders.security.CustomerActor;
import com.dienmay.customer.auth.CustomerUserDetails;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.storefront.cart.CartService;
import com.dienmay.storefront.web.CartController;
import com.dienmay.common.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;
@Controller public class OrderController {
    private final OrderPlacementService placement;private final OrderLifecycleService lifecycle;private final OrderQueryService queries;private final CheckoutPageService pages;private final CustomerService customers;private final CartService carts;private final Clock clock;
    public OrderController(OrderPlacementService placement,OrderLifecycleService lifecycle,OrderQueryService queries,CheckoutPageService pages,CustomerService customers,CartService carts,Clock clock){this.placement=placement;this.lifecycle=lifecycle;this.queries=queries;this.pages=pages;this.customers=customers;this.carts=carts;this.clock=clock;}
    private CheckoutDrafts drafts(HttpSession session){synchronized(org.springframework.web.util.WebUtils.getSessionMutex(session)){CheckoutDrafts drafts=(CheckoutDrafts)session.getAttribute("CHECKOUT_DRAFTS");if(drafts==null){drafts=new CheckoutDrafts();session.setAttribute("CHECKOUT_DRAFTS",drafts);}return drafts;}}
    @GetMapping("/checkout") public String checkout(@AuthenticationPrincipal CustomerUserDetails user,HttpSession session,Model model){var profile=customers.get(user.getCustomerId());CheckoutForm form=new CheckoutForm();form.setPlaceToken(drafts(session).open(user.getCustomerId(),CartController.state(session).snapshot()));form.setReceiverName(profile.fullName());form.setReceiverPhone(profile.phone());form.setDeliveryAddress(profile.address());model.addAttribute("checkoutForm",form);checkoutModel(user,session,model);return "storefront/checkout";}
    @PostMapping("/checkout") public String place(@ModelAttribute("checkoutForm") CheckoutForm form,BindingResult errors,@AuthenticationPrincipal CustomerUserDetails user,HttpSession session,Model model,RedirectAttributes flash){
        if(!errors.hasErrors())try{
            var draft=drafts(session).get(form.getPlaceToken(),user.getCustomerId());if(draft==null)throw new BusinessException("Trang đặt hàng đã cũ. Vui lòng mở lại từ giỏ.");
            var lines=draft.items().entrySet().stream().map(e->new OrderLineRequest(e.getKey(),e.getValue())).toList();
            OrderView result=placement.place(new PlaceOrderRequest(form.getPlaceToken(),form.getBranchId(),form.getDeliveryType(),form.getPaymentMethod(),form.getReceiverName(),form.getReceiverPhone(),form.getDeliveryAddress(),form.getNote(),lines),new CustomerActor(user.getCustomerId()));
            // Retry token cũ không được xóa giỏ MỚI khách đã thêm sau lần đặt trước.
            var cart=CartController.state(session);synchronized(cart){if(cart.snapshot().equals(draft.items()))carts.clear(cart);}
            flash.addFlashAttribute("success","Đặt hàng thành công.");return "redirect:/orders/"+result.code();
        }catch(BusinessException error){errors.reject("placement",error.getMessage());}
        checkoutModel(user,session,model);return "storefront/checkout";
    }
    private void checkoutModel(CustomerUserDetails user,HttpSession session,Model model){model.addAttribute("fullName",user.getFullName());model.addAttribute("cart",carts.view(CartController.state(session)));model.addAttribute("branches",pages.branches(CartController.state(session).snapshot()));}
    @GetMapping({"/account/orders","/orders"}) public String list(@AuthenticationPrincipal CustomerUserDetails user,@RequestParam(required=false) com.dienmay.orders.entity.OrderStatus status,@RequestParam(defaultValue="0") int page,Model model){var result=queries.customerPage(new CustomerActor(user.getCustomerId()),status,page);model.addAttribute("orders",result.getContent());model.addAttribute("result",result);model.addAttribute("status",status);return "storefront/orders/list";}
    @GetMapping("/orders/{code}") public String detail(@PathVariable String code,@AuthenticationPrincipal CustomerUserDetails user,Model model){orderModel(code,user,model);return "storefront/orders/detail";}
    @GetMapping("/checkout/pay/{orderCode}") public String payment(@PathVariable String orderCode,@AuthenticationPrincipal CustomerUserDetails user,Model model){orderModel(orderCode,user,model);return "storefront/orders/pay";}
    @PostMapping("/checkout/pay/{orderCode}") public String pay(@PathVariable String orderCode,@AuthenticationPrincipal CustomerUserDetails user,RedirectAttributes flash){try{lifecycle.pay(orderCode,new CustomerActor(user.getCustomerId()));flash.addFlashAttribute("success","Thanh toán mô phỏng thành công.");}catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}return "redirect:/orders/"+orderCode;}
    @PostMapping("/orders/{code}/cancel") public String cancel(@PathVariable String code,@RequestParam(required=false) String reason,@AuthenticationPrincipal CustomerUserDetails user,RedirectAttributes flash){try{lifecycle.cancel(code,reason,new CustomerActor(user.getCustomerId()));flash.addFlashAttribute("success","Đã hủy đơn và nhả hàng; khoản thu mô phỏng (nếu có) đã hoàn.");}catch(BusinessException error){flash.addFlashAttribute("error",error.getMessage());}return "redirect:/orders/"+code;}
    private void orderModel(String code,CustomerUserDetails user,Model model){var order=queries.get(code,new CustomerActor(user.getCustomerId()));model.addAttribute("order",order);model.addAttribute("expiresMillis",order.expiresAt()==null?null:order.expiresAt().atZone(clock.getZone()).toInstant().toEpochMilli());}
}
