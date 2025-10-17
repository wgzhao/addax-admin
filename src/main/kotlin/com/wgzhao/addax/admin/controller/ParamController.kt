package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.exception.ApiException
import com.wgzhao.addax.admin.model.SysDict
import com.wgzhao.addax.admin.model.SysItem
import com.wgzhao.addax.admin.service.DictService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.tags.Tag
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.function.Supplier

/**
 * 参数管理控制器，提供参数字典及字典项的相关接口
 */
@Tag(name = "参数管理", description = "参数字典及字典项相关接口")
@RestController
@RequestMapping("/dicts")
class ParamController(
    private val dictService: DictService
) {
    private val log = KotlinLogging.logger {}

    /**
     * 查询所有字典
     * @return 所有参数字典列表
     */
    @Operation(summary = "查询所有字典", description = "返回所有参数字典列表")
    @GetMapping("")
    fun listDicts(): List<SysDict> = dictService.findAllDicts()

    /**
     * 新建字典
     * @param dict 字典对象
     * @return 新建的字典对象
     */
    @Operation(summary = "新建字典", description = "创建一个新的参数字典")
    @PostMapping("")
    fun createDict(
        @RequestBody(
            description = "字典对象",
            required = true,
            content = [Content(schema = Schema(implementation = SysDict::class))]
        ) @org.springframework.web.bind.annotation.RequestBody dict: SysDict
    ): ResponseEntity<SysDict> {
        val result = dictService.findDictByCode(dict.code)
        return if (result == null) {
            val saved = dictService.saveDict(dict)
            ResponseEntity.status(HttpStatus.CREATED).body(saved)
        } else {
            throw ApiException(409, "Dict already exists")
        }
    }

    /**
     * 查询单个字典
     * @param dictCode 字典编码
     * @return 字典对象
     */
    @Operation(summary = "查询单个字典", description = "根据字典编码查询字典")
    @GetMapping("/{dictCode}")
    fun getDict(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int
    ): SysDict =
        dictService.findDictById(dictCode).orElseThrow { ApiException(404, "Dict not found") }

    /**
     * 更新字典
     * @param dictCode 字典编码
     * @param dict 字典对象
     * @return 更新后的字典对象
     */
    @Operation(summary = "更新字典", description = "根据字典编码更新字典")
    @PutMapping("/{dictCode}")
    fun updateDict(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int,
        @RequestBody(
            description = "字典对象",
            required = true,
            content = [Content(schema = Schema(implementation = SysDict::class))]
        ) @org.springframework.web.bind.annotation.RequestBody dict: SysDict
    ): SysDict {
        dict.code = dictCode
        return dictService.saveDict(dict)
    }

    /**
     * 删除字典
     * @param dictCode 字典编码
     * @return 无内容响应
     */
    @Operation(summary = "删除字典", description = "根据字典编码删除字典")
    @DeleteMapping("/{dictCode}")
    fun deleteDict(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int
    ): ResponseEntity<Void> {
        dictService.deleteDict(dictCode)
        return ResponseEntity.noContent().build()
    }

    /**
     * 查询字典项列表
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    @Operation(summary = "查询字典项列表", description = "查询某字典下所有字典项")
    @GetMapping("/{dictCode}/items")
    fun listItems(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int
    ): List<SysItem?> {
        return dictService.findItemsByDictCode(dictCode)
    }

    /**
     * 新建字典项
     * @param dictCode 字典编码
     * @param sysItem 字典项对象
     * @return 新建的字典项对象
     */
    @Operation(summary = "新建字典项", description = "为指定字典新建字典项")
    @PostMapping("/{dictCode}/items")
    fun createItem(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int,
        @RequestBody(
            description = "字典项对象",
            required = true,
            content = [Content(schema = Schema(implementation = SysItem::class))]
        ) @org.springframework.web.bind.annotation.RequestBody sysItem: SysItem
    ): ResponseEntity<SysItem?> {
        sysItem.dictCode = dictCode
        if (dictService.existsItem(dictCode, sysItem.itemKey)) {
            throw ApiException(409, "Item already exists")
        }
        val saved = dictService.saveItem(sysItem)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    /**
     * 查询单个字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @return 字典项对象
     */
    @Operation(summary = "查询单个字典项", description = "根据字典编码和项键查询字典项")
    @GetMapping("/{dictCode}/items/{itemKey}")
    fun getItem(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int,
        @Parameter(description = "字典项键", example = "SWITCH_TIME") @PathVariable("itemKey") itemKey: String
    ): SysItem? {
        return dictService.findItemById(dictCode, itemKey)
            .orElseThrow<ApiException?>(Supplier { ApiException(404, "Item not found") })
    }

    /**
     * 更新字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @param sysItem 字典项对象
     * @return 更新后的字典项对象
     */
    @Operation(summary = "更新字典项", description = "根据字典编码和项键更新字典项")
    @PutMapping("/{dictCode}/items/{itemKey}")
    fun updateItem(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int,
        @Parameter(description = "字典项键", example = "SWITCH_TIME") @PathVariable("itemKey") itemKey: String,
        @RequestBody(
            description = "字典项对象",
            required = true,
            content = [Content(schema = Schema(implementation = SysItem::class))]
        ) @org.springframework.web.bind.annotation.RequestBody sysItem: SysItem
    ): SysItem {
        sysItem.dictCode =  dictCode
        sysItem.itemKey = itemKey
        return dictService.saveItem(sysItem)
    }

    /**
     * 删除字典项
     * @param dictCode 字典编码
     * @param itemKey 字典项键
     * @return 无内容响应
     */
    @Operation(summary = "删除字典项", description = "根据字典编码和项键删除字典项")
    @DeleteMapping("/{dictCode}/items/{itemKey}")
    fun deleteItem(
        @Parameter(description = "字典编码", example = "1000") @PathVariable("dictCode") dictCode: Int,
        @Parameter(description = "字典项键", example = "SWITCH_TIME") @PathVariable("itemKey") itemKey: String
    ): ResponseEntity<Void?> {
        if (dictService.existsItem(dictCode, itemKey)) {
            dictService.deleteItem(dictCode, itemKey)
            return ResponseEntity.noContent().build<Void?>()
        } else {
            throw ApiException(404, "Item not found")
        }
    }

    companion object {
        /** 保留的字典编码，不能被删除  */
        private val RESERVED_DICT_CODES = mutableListOf<Int?>(1000, 1021, 2011, 5000, 5001)
    }
}
