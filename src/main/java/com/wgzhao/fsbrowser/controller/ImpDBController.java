package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpDB;
import com.wgzhao.fsbrowser.service.ImpDBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/impdb")
public class ImpDBController {

    @Autowired
    private ImpDBService impDBService;

    @GetMapping("/list")
    public String getAllImpDB(Model model) {
        model.addAttribute("impdbs",impDBService.getAllImpDB());
        return "impdb/list";
    }

    @GetMapping("/view/{id}")
    public String getImpDBById(Model model, @PathVariable(name="id") UUID id) {
        model.addAttribute("impdb", impDBService.getImpDBById(id));
        model.addAttribute("Editable", false);
        return "impdb/detail";
    }

    @GetMapping("/edit/{id}")
    public String editImpDB(@PathVariable(value="id") UUID id, Model model) {
        model.addAttribute("impdb", impDBService.getImpDBById(id));
        model.addAttribute("Editable", true);
        return "impdb/detail";
    }

    @PostMapping("/save")
    public String saveImpDB(@ModelAttribute("impDB") ImpDB impDB) {
        impDBService.saveImpDB(impDB);
        return "redirect:/impdb/list";
    }
}
