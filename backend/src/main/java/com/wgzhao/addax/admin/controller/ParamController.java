package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysDict;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.DictService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 参数管理控制器，提供参数字典及字典项的相关接口
 */
@RestController
@RequestMapping("/dicts")
@AllArgsConstructor
public class ParamController
{
    /**
     * 保留的字典编码，不能被删除
     */
    private static final List<Integer> RESERVED_DICT_CODES = List.of(1000, 1021, 2011, 5000, 5001);
    /**
     * 字典服务，用于处理字典相关业务逻辑
     */
    private final DictService dictService;

    /**
     * 查询所有字典
     *
     * @return 所有参数字典列表
     */
    @GetMapping("")
    public List<SysDict> listDicts()
    {
        return dictService.findAllDicts();
    }

    /**
     * 新建字典
     *
     * 请求体: SysDict
     *
     * @param dict 字典对象（请求体），字段详见 {@link SysDict}
     * @return 新建的字典对象（201 Created）
     */
    @PostMapping("")
    public ResponseEntity<SysDict> createDict(
        @RequestBody SysDict dict)
    {
        SysDict result = dictService.findDictByCode(dict.getCode());
        if (result == null) {
            SysDict saved = dictService.saveDict(dict);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        }
        else {
            throw new ApiException(409, "Dict already exists");
        }
    }

    /**
     * 查询单个字典
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @return 字典对象
     */
    @GetMapping("/{dictCode}")
    public SysDict getDict(
        @PathVariable("dictCode") int dictCode)
    {
        return dictService.findDictById(dictCode)
            .orElseThrow(() -> new ApiException(404, "Dict not found"));
    }

    /**
     * 更新字典
     * 请求体: SysDict
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @param dict 字典对象（请求体），字段详见 {@link SysDict}
     * @return 更新后的字典对象
     */
    @PutMapping("/{dictCode}")
    public SysDict updateDict(
        @PathVariable int dictCode,
        @RequestBody SysDict dict)
    {
        dict.setCode(dictCode);
        return dictService.saveDict(dict);
    }

    /**
     * 删除字典
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @return 无内容响应
     */
    @DeleteMapping("/{dictCode}")
    public ResponseEntity<Void> deleteDict(
        @PathVariable int dictCode)
    {
        if (RESERVED_DICT_CODES.contains(dictCode)) {
            throw new ApiException(400, "Cannot delete reserved dict");
        }
        if (dictService.existsDict(dictCode)) {
            dictService.deleteDict(dictCode);
            return ResponseEntity.noContent().build();
        }
        else {
            throw new ApiException(404, "Dict not found");
        }
    }

    /**
     * 查询字典项列表
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @return 字典项列表
     */
    @GetMapping("/{dictCode}/items")
    public List<SysItem> listItems(
        @PathVariable int dictCode)
    {
        return dictService.findItemsByDictCode(dictCode);
    }

    /**
     * 新建字典项
     * 请求体: SysItem
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @param sysItem 字典项对象（请求体），字段详见 {@link SysItem}
     * @return 新建的字典项对象（201 Created）
     */
    @PostMapping("/{dictCode}/items")
    public ResponseEntity<SysItem> createItem(
        @PathVariable int dictCode,
        @RequestBody SysItem sysItem)
    {
        sysItem.setDictCode(dictCode);
        if (dictService.existsItem(dictCode, sysItem.getItemKey())) {
            throw new ApiException(409, "Item already exists");
        }
        SysItem saved = dictService.saveItem(sysItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * 查询单个字典项
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @param itemKey 字典项键（路径参数），示例: SWITCH_TIME
     * @return 字典项对象
     */
    @GetMapping("/{dictCode}/items/{itemKey}")
    public SysItem getItem(
        @PathVariable int dictCode,
        @PathVariable String itemKey)
    {
        return dictService.findItemById(dictCode, itemKey)
            .orElseThrow(() -> new ApiException(404, "Item not found"));
    }

    /**
     * 更新字典项
     * 请求体: SysItem
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @param itemKey 字典项键（路径参数），示例: SWITCH_TIME
     * @param sysItem 字典项对象（请求体），字段详见 {@link SysItem}
     * @return 更新后的字典项对象
     */
    @PutMapping("/{dictCode}/items/{itemKey}")
    public SysItem updateItem(
        @PathVariable int dictCode,
        @PathVariable String itemKey,
        @RequestBody SysItem sysItem)
    {
        sysItem.setDictCode(dictCode);
        sysItem.setItemKey(itemKey);
        return dictService.saveItem(sysItem);
    }

    /**
     * 删除字典项
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @param itemKey 字典项键（路径参数），示例: SWITCH_TIME
     * @return 无内容响应
     */
    @DeleteMapping("/{dictCode}/items/{itemKey}")
    public ResponseEntity<Void> deleteItem(
        @PathVariable int dictCode,
        @PathVariable String itemKey)
    {
        if (dictService.existsItem(dictCode, itemKey)) {
            dictService.deleteItem(dictCode, itemKey);
            return ResponseEntity.noContent().build();
        }
        else {
            throw new ApiException(404, "Item not found");
        }
    }

    /**
     * 获取默认的 HDFS 存储格式和压缩格式
     */
    @GetMapping("/hdfs-storage-defaults")
    public ResponseEntity<Map<String, String>> getHdfsDefaults()
    {
        Map<String, String> defaults = dictService.getHdfsDefaultFormats();
        return ResponseEntity.ok(defaults);
    }

    /**
     * 生成指定年份的交易日历,同时包含一个可选参数，是否包含周末
     */
    @PostMapping("/generate-trade-calendar/{year}/{includeWeekend}")
    public ResponseEntity<Void> generateTradeCalendar(@PathVariable int year,
                                                      @PathVariable boolean includeWeekend)
    {
        dictService.generateTradeCalendar(year, includeWeekend);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
