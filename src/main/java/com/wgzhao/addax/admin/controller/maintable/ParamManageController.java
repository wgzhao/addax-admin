package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.TbDict;
import com.wgzhao.addax.admin.model.oracle.TbDictionary;
import com.wgzhao.addax.admin.model.oracle.TbDictionaryPK;
import com.wgzhao.addax.admin.repository.oracle.TbDictRepo;
import com.wgzhao.addax.admin.repository.oracle.TbDictionaryRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    @GetMapping("/dicts")
    public List<TbDict> listDicts() {
        return dictRepo.findAll();
    }

    @GetMapping("/dicts/{code}")
    public Optional<TbDict> getDict(@PathVariable("code") String code)
    {
        return dictRepo.findById(code);
    }

    @PostMapping("/dicts")
    public TbDict createOrSaveDict(@RequestBody TbDict dict) {
        TbDict result = dictRepo.findByDictCode(dict.getDictCode());
        if (result == null ) {
            return dictRepo.save(dict);
        } else {
            return dict;
        }
    }

    @DeleteMapping("/dicts/{id}")
    public int deleteDict(@PathVariable(value = "id") String id) {
        TbDict dict = dictRepo.findByDictCode(id);
        if (dict != null) {
            dictRepo.delete(dict);
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * 根据 {@link TbDict} 的 `dict_code` 来读取详细的字典编码
     * @param entryCode String the dict_code value
     * @return list of {@link TbDictionary}
     */
    @GetMapping("/dictionaries/{entryCode}")
    public List<TbDictionary> getDictByEntryCode(@PathVariable("entryCode") String entryCode)
    {
        return dictionaryRepo.findByEntryCodeOrderByEntryCodeAsc(entryCode);
    }

    @PostMapping("/dictionaries")
    public TbDictionary createOrSaveDictionary(@RequestBody TbDictionary tbDictionary) {
        return dictionaryRepo.save(tbDictionary);
    }

    @PutMapping("/dictionaries")
    public List<TbDictionary> bulkCreateDictionary(@RequestBody List<TbDictionary> tbDictionaries) {
        return dictionaryRepo.saveAll(tbDictionaries);
    }

//    @DeleteMapping("/dictionaries/{entryCode}")
//    public int deleteDictionary(@PathVariable(value = "entryCode") String entryCode) {
//        if (dictionaryRepo.findByEntryCode(entryCode)) {
//            dictionaryRepo.deleteById(entryCode);
//            return 1;
//        } else {
//            return 0;
//        }
//    }

    @DeleteMapping("/dictionaries/{entryCode}/{entryValue}")
    public int deleteDictionaryItem(@PathVariable(value = "entryCode") String entryCode, @PathVariable(value = "entryValue") String entryValue) {
        TbDictionaryPK tbDictionary = new TbDictionaryPK();
        tbDictionary.setEntryCode(entryCode);
        tbDictionary.setEntryValue(entryValue);
        if (dictionaryRepo.existsById(tbDictionary)) {
            dictionaryRepo.deleteById(tbDictionary);
            return 1;
        } else {
            return 0;
        }
    }

}
