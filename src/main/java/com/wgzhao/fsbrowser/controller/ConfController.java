package com.wgzhao.fsbrowser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/conf")
public class ConfController {

    @GetMapping("/index")
    public String index()
    {
        return "conf/index";
    }
}
