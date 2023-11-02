package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.User;
import com.wgzhao.fsbrowser.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class UserController {

    private UserService userService;

    public UserController(UserService userService)
    {
        this.userService = userService;
    }

    @ModelAttribute("user")
    public User userRegistration()
    {
        return new User();
    }

    @GetMapping("/register")
    public String register()
    {
        return "register";
    }

    @PostMapping("/register")
    public String registerAccount(@ModelAttribute("user") User user)
    {
        if (userService.existsByUsername(user.getUsername())) {
            return "redirect:/register?failed";
        }
        userService.save(user);
        return "redirect:/register?success";
    }

    @GetMapping("/login")
    public String login()
    {
        return "login";
    }

    @GetMapping("/home")
    public String home()
    {
        return "home";
    }

    @GetMapping("/failed")
    public String failed()
    {
        return "failed";
    }

}
