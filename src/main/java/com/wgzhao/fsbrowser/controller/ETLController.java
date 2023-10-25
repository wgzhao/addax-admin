package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.service.ImpEtlOverprecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/etl")
public class ETLController {

        @Autowired
        private ImpEtlOverprecService impEtlOverprecService;
        @RequestMapping("/index")
        public String index(Model model)
        {
            model.addAttribute("impEtlOverprec", impEtlOverprecService.getAllImpEtlOverprec());
            return "etl/index";
        }
}
