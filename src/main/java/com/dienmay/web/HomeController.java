package com.dienmay.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/staff")
    public String home() {
        return "home";
    }
}
