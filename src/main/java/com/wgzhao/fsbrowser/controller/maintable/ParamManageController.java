package com.wgzhao.fsbrowser.controller.maintable;

import com.wgzhao.fsbrowser.model.oracle.TbDict;
import com.wgzhao.fsbrowser.model.oracle.TbDictionary;
import com.wgzhao.fsbrowser.repository.oracle.TbDictRepo;
import com.wgzhao.fsbrowser.repository.oracle.TbDictionaryRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 参数管理配置接口
 */
@Api(value="参数管理配置接口", tags = {"主表配置"})
@RestController
@RequestMapping("/maintable/paramManage")
public class ParamManageController {

    @Autowired
    private TbDictRepo dictRepo;

    @Autowired
    private TbDictionaryRepo dictionaryRepo;

    /**
     * query dict
     * @return List of {@link TbDict}
     */
    @GetMapping("/list")
    public List<TbDict> getAllDict() {
        return dictRepo.findAll();
    }

    @GetMapping("/detail/{code}")
    public Optional<TbDict> getDict(@PathVariable("code") String code)
    {
        return dictRepo.findById(code);
    }

    @GetMapping("/dictionary/{entryCode}")
    public List<TbDictionary> getDictByEntryCode(@PathVariable("entryCode") String entryCode)
    {
        return dictionaryRepo.findByEntryCodeOrderByEntryCodeAsc(entryCode);
    }
    @PostMapping("/save")
    public String saveDict(@ModelAttribute("dict") TbDict  dict) {
        TbDict result = dictRepo.findByDictCode(dict.getDictCode());
        if (result == null ) {
            dictRepo.save(dict);
            return "redirect:/dict/list";
        } else {
            return "The dictCode " +  dict.getDictCode() + " has exists";
        }

    }

    @GetMapping("/delete/{id}")
    public String deleteDict(@PathVariable(value = "id") String id) {
        TbDict dict = dictRepo.findByDictCode(id);
        if (dict != null) {
            dictRepo.delete(dict);
        }
        return "redirect:/dict/list";
    }
}
