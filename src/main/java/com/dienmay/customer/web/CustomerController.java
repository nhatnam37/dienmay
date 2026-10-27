package com.dienmay.customer.web;
import com.dienmay.customer.auth.*;
import com.dienmay.customer.dto.*;
import com.dienmay.customer.mapper.CustomerMapper;
import com.dienmay.customer.service.CustomerService;
import com.dienmay.common.exception.BusinessException;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller public class CustomerController {
    private final CustomerService customers;private final CustomerSessionLogin login;private final CustomerMapper mapper;
    public CustomerController(CustomerService customers,CustomerSessionLogin login,CustomerMapper mapper){this.customers=customers;this.login=login;this.mapper=mapper;}
    @GetMapping("/customer/login") public String login(){return "customer/login";}
    @GetMapping("/register") public String register(Model model){model.addAttribute("registrationForm",new RegistrationForm());return "customer/register";}
    @PostMapping("/register") public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,BindingResult result,HttpServletRequest request,HttpServletResponse response){
        if(!result.hasErrors())try{var customer=customers.register(form);login.login(customer.email(),request,response);var cache=new HttpSessionRequestCache();cache.setSessionAttrName("CUSTOMER_SAVED_REQUEST");var saved=cache.getRequest(request,response);cache.removeRequest(request,response);return saved!=null&&java.net.URI.create(saved.getRedirectUrl()).getPath().equals("/checkout")?"redirect:/checkout":"redirect:/account";}catch(BusinessException error){result.reject("registration",error.getMessage());}
        return "customer/register";
    }
    @GetMapping("/account") public String account(@AuthenticationPrincipal CustomerUserDetails user,Model model){accountModel(user,model);return "customer/account";}
    @PostMapping("/account") public String profile(@Valid @ModelAttribute("profileForm") ProfileForm form,BindingResult result,@AuthenticationPrincipal CustomerUserDetails user,Model model,HttpServletRequest request,HttpServletResponse response,RedirectAttributes flash){
        if(!result.hasErrors())try{var customer=customers.update(user.getCustomerId(),form);login.login(customer.email(),request,response);flash.addFlashAttribute("success","Đã cập nhật hồ sơ.");return "redirect:/account";}catch(BusinessException error){result.reject("profile",error.getMessage());}accountModel(user,model);return "customer/account";
    }
    @PostMapping("/account/password") public String password(@Valid @ModelAttribute("passwordForm") PasswordForm form,BindingResult result,@AuthenticationPrincipal CustomerUserDetails user,Model model,HttpServletRequest request,HttpServletResponse response,RedirectAttributes flash){
        if(!result.hasErrors())try{var customer=customers.changePassword(user.getCustomerId(),form);login.login(customer.email(),request,response);flash.addFlashAttribute("success","Đã đổi mật khẩu.");return "redirect:/account";}catch(BusinessException error){result.reject("password",error.getMessage());}accountModel(user,model);return "customer/account";
    }
    private void accountModel(CustomerUserDetails user,Model model){var customer=customers.get(user.getCustomerId());model.addAttribute("customer",customer);if(!model.containsAttribute("profileForm"))model.addAttribute("profileForm",mapper.form(customer));if(!model.containsAttribute("passwordForm"))model.addAttribute("passwordForm",new PasswordForm());}
}
