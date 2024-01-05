package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.TbDictionary;
import com.wgzhao.fsbrowser.repository.oracle.TbDictionaryRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/dictionary")
public class DictionaryController {

    @Autowired
    private TbDictionaryRepo dictionaryRepo;

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable(value = "id") String id, Model model) {
        model.addAttribute("dictionary", dictionaryRepo.findByEntryCode(id));
        return "dictionary/detail";
    }

    @GetMapping("/list/{id}")
    public String list(@PathVariable(value = "id") String id, Model model) {
        List<TbDictionary> dicts = dictionaryRepo.findByEntryCode(id);
        System.out.println(dicts.get(2).getEntryValue());
        model.addAttribute("dicts", dicts);
        return "dictionary/list";
    }
}
