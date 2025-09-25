package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysDict;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "参数管理", description = "参数字典及字典项相关接口")
@RestController
@RequestMapping("/dicts")
@AllArgsConstructor
public class ParamController
{

    private final DictService dictService;

    private static final List<Integer> RESERVED_DICT_CODES = List.of(1000, 1021, 2011, 5000, 5001);

    @Operation(summary = "查询所有字典", description = "返回所有参数字典列表")
    @GetMapping("")
    public List<SysDict> listDicts()
    {
        return dictService.findAllDicts();
    }

    @Operation(summary = "新建字典", description = "创建一个新的参数字典")
    @PostMapping("")
    public ResponseEntity<SysDict> createDict(
            @RequestBody(description = "字典对象", required = true, content = @Content(schema = @Schema(implementation = SysDict.class)))
            @org.springframework.web.bind.annotation.RequestBody SysDict dict)
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

    @Operation(summary = "查询单个字典", description = "根据字典编码查询字典")
    @GetMapping("/{dictCode}")
    public SysDict getDict(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode)
    {
        return dictService.findDictById(dictCode)
                .orElseThrow(() -> new ApiException(404, "Dict not found"));
    }

    @Operation(summary = "更新字典", description = "根据字典编码更新字典")
    @PutMapping("/{dictCode}")
    public SysDict updateDict(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode,
            @RequestBody(description = "字典对象", required = true, content = @Content(schema = @Schema(implementation = SysDict.class)))
            @org.springframework.web.bind.annotation.RequestBody SysDict dict)
    {
        dict.setCode(dictCode);
        return dictService.saveDict(dict);
    }

    @Operation(summary = "删除字典", description = "根据字典编码删除字典")
    @DeleteMapping("/{dictCode}")
    public ResponseEntity<Void> deleteDict(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode)
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

    @Operation(summary = "查询字典项列表", description = "查询某字典下所有字典项")
    @GetMapping("/{dictCode}/items")
    public List<SysItem> listItems(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode)
    {
        return dictService.findItemsByDictCode(dictCode);
    }

    @Operation(summary = "新建字典项", description = "为指定字典新建字典项")
    @PostMapping("/{dictCode}/items")
    public ResponseEntity<SysItem> createItem(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode,
            @RequestBody(description = "字典项对象", required = true, content = @Content(schema = @Schema(implementation = SysItem.class)))
            @org.springframework.web.bind.annotation.RequestBody SysItem sysItem)
    {
        sysItem.setDictCode(dictCode);
        if (dictService.existsItem(dictCode, sysItem.getItemKey())) {
            throw new ApiException(409, "Item already exists");
        }
        SysItem saved = dictService.saveItem(sysItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @Operation(summary = "查询单个字典项", description = "根据字典编码和项键查询字典项")
    @GetMapping("/{dictCode}/items/{itemKey}")
    public SysItem getItem(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode,
            @Parameter(description = "字典项键", example = "SWITCH_TIME") @PathVariable("itemKey") String itemKey)
    {
        return dictService.findItemById(dictCode, itemKey)
                .orElseThrow(() -> new ApiException(404, "Item not found"));
    }

    @Operation(summary = "更新字典项", description = "根据字典编码和项键更新字典项")
    @PutMapping("/{dictCode}/items/{itemKey}")
    public SysItem updateItem(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode,
            @Parameter(description = "字典项键", example = "SWITCH_TIME") @PathVariable("itemKey") String itemKey,
            @RequestBody(description = "字典项对象", required = true, content = @Content(schema = @Schema(implementation = SysItem.class)))
            @org.springframework.web.bind.annotation.RequestBody SysItem sysItem)
    {
        sysItem.setDictCode(dictCode);
        sysItem.setItemKey(itemKey);
        return dictService.saveItem(sysItem);
    }

    @Operation(summary = "删除字典项", description = "根据字典编码和项键删除字典项")
    @DeleteMapping("/{dictCode}/items/{itemKey}")
    public ResponseEntity<Void> deleteItem(
            @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") int dictCode,
            @Parameter(description = "字典项键", example = "SWITCH_TIME") @PathVariable("itemKey") String itemKey)
    {
        if (dictService.existsItem(dictCode, itemKey)) {
            dictService.deleteItem(dictCode, itemKey);
            return ResponseEntity.noContent().build();
        }
        else {
            throw new ApiException(404, "Item not found");
        }
    }
}
