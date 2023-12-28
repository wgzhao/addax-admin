package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.Dict;
import com.wgzhao.fsbrowser.repository.oracle.DictRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController
@RequestMapping("/dict")
@CrossOrigin
public class DictController {

    @Autowired
    private DictRepo dictRepo;

    /**
     * query dict
     * @return List of {@link Dict}
     */
    @GetMapping("/list")
    public List<Dict> getAllDict() {
        return dictRepo.findAll();
    }

    @GetMapping("/{code}")
    public Dict getDict(@PathVariable("code") String code)
    {
        return dictRepo.findByDictCode(code);
    }

    @GetMapping("/add")
    public String addDict(Model model) {
        // prepare new dict for create
        model.addAttribute("emptyDict", new Dict());
        return "dict/detail";
    }

    @GetMapping("/edit/{id}")
    public String editDict(@PathVariable(value = "id") String id, Model model) {
        Dict dict = dictRepo.findByDictCode(id);
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
        Dict result = dictRepo.findByDictCode(dict.getDictCode());
        if (result == null ) {
            dictRepo.save(dict);
            return "redirect:/dict/list";
        } else {
            return "The dictCode " +  dict.getDictCode() + " has exists";
        }

    }

    @GetMapping("/delete/{id}")
    public String deleteDict(@PathVariable(value = "id") String id) {
        Dict dict = dictRepo.findByDictCode(id);
        if (dict != null) {
            dictRepo.delete(dict);
        }
        return "redirect:/dict/list";
    }
}
