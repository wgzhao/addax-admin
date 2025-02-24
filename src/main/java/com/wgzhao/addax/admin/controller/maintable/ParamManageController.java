package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.oracle.TbDict;
import com.wgzhao.addax.admin.model.oracle.TbDictionary;
import com.wgzhao.addax.admin.model.oracle.TbDictionaryPK;
import com.wgzhao.addax.admin.repository.oracle.TbDictRepo;
import com.wgzhao.addax.admin.repository.oracle.TbDictionaryRepo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
     *
     * @return List of {@link TbDict}
     */
    @GetMapping("/dicts")
    public ApiResponse<List<TbDict>> listDicts() {
        return ApiResponse.success(dictRepo.findAll());
    }

    @GetMapping("/dicts/{code}")
    public ApiResponse<Optional<TbDict>> getDict(@PathVariable("code") String code)
    {
        return ApiResponse.success(dictRepo.findById(code));
    }

    @PostMapping("/dicts")
    public ApiResponse<TbDict> createOrSaveDict(@RequestBody TbDict dict) {
        TbDict result = dictRepo.findByDictCode(dict.getDictCode());
        if (result == null ) {
            return ApiResponse.success(dictRepo.save(dict));
        } else {
            return ApiResponse.success(dict);
        }
    }

    @DeleteMapping("/dicts/{id}")
    public ApiResponse<Integer> deleteDict(@PathVariable(value = "id") String id) {
        TbDict dict = dictRepo.findByDictCode(id);
        if (dict != null) {
            dictRepo.delete(dict);
            return ApiResponse.success(1);
        } else {
            return ApiResponse.success(0);
        }
    }

    /**
     * 根据 {@link TbDict} 的 `dict_code` 来读取详细的字典编码
     *
     * @param entryCode String the dict_code value
     * @return list of {@link TbDictionary}
     */
    @GetMapping("/dictionaries/{entryCode}")
    public ApiResponse<List<TbDictionary>> getDictByEntryCode(@PathVariable("entryCode") String entryCode)
    {
        return ApiResponse.success(dictionaryRepo.findByEntryCodeOrderByEntryCodeAsc(entryCode));
    }

    @PostMapping("/dictionaries")
    public ApiResponse<TbDictionary> createOrSaveDictionary(@RequestBody TbDictionary tbDictionary) {
        return ApiResponse.success(dictionaryRepo.save(tbDictionary));
    }

    @PutMapping("/dictionaries")
    public ApiResponse<Integer> bulkCreateDictionary(@RequestBody List<TbDictionary> tbDictionaries) {
        List<TbDictionary> tbDictionaries1 = dictionaryRepo.saveAll(tbDictionaries);
        return ApiResponse.success(tbDictionaries1.size());
    }


    @DeleteMapping("/dictionaries/{entryCode}/{entryValue}")
    public ApiResponse<Integer> deleteDictionaryItem(@PathVariable(value = "entryCode") String entryCode, @PathVariable(value = "entryValue") String entryValue) {
        TbDictionaryPK tbDictionary = new TbDictionaryPK();
        tbDictionary.setEntryCode(entryCode);
        tbDictionary.setEntryValue(entryValue);
        if (dictionaryRepo.existsById(tbDictionary)) {
            dictionaryRepo.deleteById(tbDictionary);
            return ApiResponse.success(1);
        } else {
            return ApiResponse.success(0);
        }
    }

}
