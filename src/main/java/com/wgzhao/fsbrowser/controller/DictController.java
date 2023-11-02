package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.Dict;
import com.wgzhao.fsbrowser.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/dict")
public class DictController {

    @Autowired
    DictService dictService;

    @GetMapping("/list")
    public String getAllDict(Model model) {
        model.addAttribute("dicts", dictService.getAllDict());
        return "dict/list";
    }

    @GetMapping("/add")
    public String addDict(Model model) {
        // prepare new dict for create
        model.addAttribute("emptyDict", new Dict());
        return "dict/detail";
    }

    @GetMapping("/edit/{id}")
    public String editDict(@PathVariable(value = "id") String id, Model model) {
        Dict dict = dictService.findByCode(id);
        if (dict != null) {
            model.addAttribute("emptyDict", dict);
            return "dict/detail";
        } else {
            return "redirect:/dict/list";
        }
    }

    @GetMapping("/detail/{id}")
    public String detailDict() {
        return "dict/detail";
    }

    @PostMapping("/save")
    public String saveDict(@ModelAttribute("dict") Dict  dict) {
        Dict result = dictService.findByCode(dict.getDictCode());
        if (result == null ) {
            dictService.save(dict);
            return "redirect:/dict/list";
        } else {
            return "The dictCode " +  dict.getDictCode() + " has exists";
        }

    }

    @GetMapping("/delete/{id}")
    public String deleteDict(@PathVariable(value = "id") String id) {
        Dict dict = dictService.findByCode(id);
        if (dict != null) {
            dictService.delete(dict);
        }
        return "redirect:/dict/list";
    }
}
