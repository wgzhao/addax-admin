package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpDB;
import com.wgzhao.fsbrowser.service.ImpDBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/impdb")
@CrossOrigin
public class ImpDBController {

    @Autowired
    private ImpDBService impDBService;

    @GetMapping("/list")
    public List<ImpDB> getAllImpDB() {
        return impDBService.getAllImpDB();
    }

    @GetMapping("/add")
    public String addImpDB(Model model) {
        ImpDB impDB = new ImpDB();
        impDB.setId(UUID.randomUUID().toString());
        model.addAttribute("d", impDB);
        model.addAttribute("editable", true);
        return "impdb/detail";
    }

    @GetMapping("/detail/{id}")
    public ImpDB getImpDBById(Model model, @PathVariable(value="id") String id) {
        return impDBService.getImpDBById(id);
    }

    @GetMapping("/edit/{id}")
    public String editImpDB(@PathVariable(value="id") String id, Model model) {
        model.addAttribute("d", impDBService.getImpDBById(id));
        model.addAttribute("editable", true);
        return "impdb/detail";
    }

    @PostMapping("/save")
    public ImpDB saveImpDB(@ModelAttribute("impDB") ImpDB impDB) {
        if (impDB.getId() == null || Objects.equals(impDB.getId(), "")) {
            impDB.setId(UUID.randomUUID().toString());
        }
        impDBService.saveImpDB(impDB);
        return impDB;
    }
}
