package com.wgzhao.fsbrowser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/etl")
public class ETLController {

        @RequestMapping("/index")
        public String index()
        {
            return "etl/index";
        }
}
