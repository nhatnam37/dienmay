package com.dienmay.storefront.web;
import com.dienmay.customer.web.CustomerController;
import com.dienmay.common.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
@ControllerAdvice(assignableTypes={StorefrontController.class,CartController.class,CustomerController.class,com.dienmay.orders.web.OrderController.class})
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class StorefrontExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    public String missing(){return "storefront/not-found";}
}
