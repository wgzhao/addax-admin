package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.SysDict;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.model.SysItemPK;
import com.wgzhao.addax.admin.repository.SysDictRepo;
import com.wgzhao.addax.admin.repository.SysItemRepo;
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
@Api(value="参数管理配置接口")
@RestController
@RequestMapping("/param")
public class ParamController
{

    @Autowired
    private SysDictRepo sysDictRepo;

    @Autowired
    private SysItemRepo sysItemRepo;

    /**
     * query dict
     *
     * @return List of {@link SysDict}
     */
    @GetMapping("/dicts")
    public ApiResponse<List<SysDict>> listDicts() {
        return ApiResponse.success(sysDictRepo.findAll());
    }

    @GetMapping("/dicts/{code}")
    public ApiResponse<Optional<SysDict>> getDict(@PathVariable("code") int code)
    {
        return ApiResponse.success(sysDictRepo.findById(code));
    }

    @PostMapping("/dicts")
    public ApiResponse<SysDict> createOrSaveDict(@RequestBody SysDict dict) {
        SysDict result = sysDictRepo.findByCode(dict.getCode());
        if (result == null ) {
            return ApiResponse.success(sysDictRepo.save(dict));
        } else {
            return ApiResponse.success(dict);
        }
    }

    @DeleteMapping("/dicts/{id}")
    public ApiResponse<Integer> deleteDict(@PathVariable(value = "id") int id) {
        SysDict dict = sysDictRepo.findById(id).orElse(null);
        if (dict != null) {
            sysDictRepo.delete(dict);
            return ApiResponse.success(1);
        } else {
            return ApiResponse.success(0);
        }
    }

    /**
     * 根据 {@link SysDict} 的 `dict_code` 来读取详细的字典编码
     *
     * @param entryCode String the dict_code value
     * @return list of {@link SysItem}
     */
    @GetMapping("/dictionaries/{entryCode}")
    public ApiResponse<List<SysItem>> getDictByEntryCode(@PathVariable("entryCode") int entryCode)
    {
        return ApiResponse.success(sysItemRepo.findByDictCodeOrderByDictCodeAsc(entryCode));
    }

    @PostMapping("/dictionaries")
    public ApiResponse<SysItem> createOrSaveDictionary(@RequestBody SysItem sysItem) {
        return ApiResponse.success(sysItemRepo.save(sysItem));
    }

    @PutMapping("/dictionaries")
    public ApiResponse<Integer> bulkCreateDictionary(@RequestBody List<SysItem> tbDictionaries) {
        List<SysItem> tbDictionaries1 = sysItemRepo.saveAll(tbDictionaries);
        return ApiResponse.success(tbDictionaries1.size());
    }


    @DeleteMapping("/dictionaries/{entryCode}/{entryValue}")
    public ApiResponse<Integer> deleteDictionaryItem(@PathVariable(value = "entryCode") int entryCode, @PathVariable(value = "entryValue") String entryValue) {
        SysItemPK tbDictionary = new SysItemPK();
        tbDictionary.setDictCode(entryCode);
        tbDictionary.setItemKey(entryValue);
        if (sysItemRepo.existsById(tbDictionary)) {
            sysItemRepo.deleteById(tbDictionary);
            return ApiResponse.success(1);
        } else {
            return ApiResponse.success(0);
        }
    }

}
