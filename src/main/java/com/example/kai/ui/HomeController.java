package com.example.kai.ui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.kai.config.UserConfigService;

@Controller
public class HomeController {

    private final UserConfigService config;

    public HomeController(UserConfigService config) {
        this.config = config;
    }

    @GetMapping("/")
    public String home() {
        return config.setupComplete() ? "redirect:/impact" : "redirect:/setup";
    }
}
