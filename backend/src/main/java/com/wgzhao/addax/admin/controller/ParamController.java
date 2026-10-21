package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.common.CalendarType;
import com.wgzhao.addax.admin.model.SysDict;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.CalendarService;
import com.wgzhao.addax.admin.service.TradeCalendarService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
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
    private static final List<Integer> RESERVED_DICT_CODES = List.of(1000, 1021, 1022, 2011, 5000, 5001);
    /**
     * 字典服务，用于处理字典相关业务逻辑
     */
    private final DictService dictService;
    private final CalendarService calendarService;
    private final TradeCalendarService tradeCalendarService;
    private final AuditRecorder auditRecorder;

    /**
     * 日历覆盖范围：每个日历字典最后覆盖到哪一天，以及是否需要提醒维护者导入后续日期。
     *
     * 日历耗尽不会报错，只会让使用它的源静默停止采集，所以必须显式暴露出来。
     */
    public record CalendarCoverage(int code, String calendar, String lastDate, String warning)
    {
    }

    @GetMapping("/calendar-coverage")
    public List<CalendarCoverage> calendarCoverage()
    {
        List<CalendarCoverage> rows = new ArrayList<>();
        for (CalendarType type : CalendarType.values()) {
            if (!type.isDictBacked()) {
                continue;
            }
            rows.add(new CalendarCoverage(type.getDictCode(), type.name(),
                calendarService.lastCoveredDate(type.name()).map(LocalDate::toString).orElse(null),
                calendarService.coverageWarning(type.name()).orElse(null)));
        }
        return rows;
    }

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
    @Transactional
    public ResponseEntity<SysDict> createDict(
        @RequestBody SysDict dict)
    {
        SysDict result = dictService.findDictByCode(dict.getCode());
        if (result == null) {
            SysDict saved = dictService.saveDict(dict);
            auditRecorder.success("DICT_CREATED", "CONFIG", "WARNING", "DICT",
                saved.getCode(), saved.getName(), null, saved);
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
    @Transactional
    public SysDict updateDict(
        @PathVariable int dictCode,
        @RequestBody SysDict dict)
    {
        SysDict before = dictService.findDictById(dictCode).orElse(null);
        dict.setCode(dictCode);
        SysDict saved = dictService.saveDict(dict);
        auditRecorder.success("DICT_UPDATED", "CONFIG", "WARNING", "DICT",
            dictCode, saved.getName(), before, saved);
        return saved;
    }

    /**
     * 删除字典
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @return 无内容响应
     */
    @DeleteMapping("/{dictCode}")
    @Transactional
    public ResponseEntity<Void> deleteDict(
        @PathVariable int dictCode)
    {
        if (RESERVED_DICT_CODES.contains(dictCode)) {
            throw new ApiException(400, "Cannot delete reserved dict");
        }
        if (dictService.existsDict(dictCode)) {
            SysDict before = dictService.findDictById(dictCode).orElse(null);
            dictService.deleteDict(dictCode);
            auditRecorder.success("DICT_DELETED", "CONFIG", "WARNING", "DICT",
                dictCode, before == null ? null : before.getName(), before, null);
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
    @Transactional
    public ResponseEntity<SysItem> createItem(
        @PathVariable int dictCode,
        @RequestBody SysItem sysItem)
    {
        sysItem.setDictCode(dictCode);
        if (dictService.existsItem(dictCode, sysItem.getItemKey())) {
            throw new ApiException(409, "Item already exists");
        }
        SysItem saved = dictService.saveItem(sysItem);
        auditRecorder.success("DICT_ITEM_CREATED", "CONFIG", "WARNING", "DICT_ITEM",
            dictCode + ":" + saved.getItemKey(), saved.getItemKey(), null, saved);
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
    @Transactional
    public SysItem updateItem(
        @PathVariable int dictCode,
        @PathVariable String itemKey,
        @RequestBody SysItem sysItem)
    {
        SysItem before = dictService.findItemById(dictCode, itemKey).orElse(null);
        sysItem.setDictCode(dictCode);
        sysItem.setItemKey(itemKey);
        SysItem saved = dictService.saveItem(sysItem);
        auditRecorder.success("DICT_ITEM_UPDATED", "CONFIG", "WARNING", "DICT_ITEM",
            dictCode + ":" + itemKey, itemKey, before, saved);
        return saved;
    }

    /**
     * 删除字典项
     *
     * @param dictCode 字典编码（路径参数），示例: 1000
     * @param itemKey 字典项键（路径参数），示例: SWITCH_TIME
     * @return 无内容响应
     */
    @DeleteMapping("/{dictCode}/items/{itemKey}")
    @Transactional
    public ResponseEntity<Void> deleteItem(
        @PathVariable int dictCode,
        @PathVariable String itemKey)
    {
        if (dictService.existsItem(dictCode, itemKey)) {
            SysItem before = dictService.findItemById(dictCode, itemKey).orElse(null);
            dictService.deleteItem(dictCode, itemKey);
            auditRecorder.success("DICT_ITEM_DELETED", "CONFIG", "WARNING", "DICT_ITEM",
                dictCode + ":" + itemKey, itemKey, before, null);
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
     * 生成指定年份的日历（交易日历 1021 / 工作日历 1022），includeWeekend 控制是否包含周末。
     *
     * 工作日历生成的是“周一至周五”的起点，节假日与调休仍需维护者按官方安排增删。
     */
    @PostMapping("/{dictCode}/generate-calendar/{year}/{includeWeekend}")
    @Transactional
    public ResponseEntity<Void> generateCalendar(@PathVariable int dictCode,
                                                 @PathVariable int year,
                                                 @PathVariable boolean includeWeekend)
    {
        if (CalendarType.ofDictCode(dictCode).isEmpty()) {
            throw new ApiException(400, "字典 " + dictCode + " 不是日历字典");
        }
        int added = dictService.generateCalendar(dictCode, year, includeWeekend);
        auditRecorder.success("CALENDAR_GENERATED", "CONFIG", "WARNING", "DICT",
            dictCode, "calendar " + year, null, null,
            Map.of("year", year, "includeWeekend", includeWeekend, "added", added));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 年度补全交易日历（1021）：优先从柜台接口取 SSE 休市日，取不到（超时、接口变动、未发布）时给出
     * 提示并退化为固定规则（周末不算，元旦 1 月 1-3 日、国庆 10 月 1-7 日休市）。
     *
     * <p>可重复执行：官方数据会补齐并移除该年已不再交易的日子，退化数据只补不删。</p>
     */
    @PostMapping("/{dictCode}/refresh-trade-calendar/{year}")
    @Transactional
    public ResponseEntity<TradeCalendarService.RefreshResult> refreshTradeCalendar(
        @PathVariable int dictCode,
        @PathVariable int year)
    {
        if (!CalendarType.TRADE_DAY.getDictCode().equals(dictCode)) {
            throw new ApiException(400, "仅交易日历（" + CalendarType.TRADE_DAY.getDictCode() + "）支持年度补全");
        }
        if (year < 2000 || year > 2100) {
            throw new ApiException(400, "年份超出范围：" + year);
        }
        TradeCalendarService.RefreshResult result = tradeCalendarService.refresh(year);
        auditRecorder.success("TRADE_CALENDAR_REFRESHED", "CONFIG", "WARNING", "DICT",
            dictCode, "trade calendar " + year, null, result,
            Map.of("year", year));
        return ResponseEntity.ok(result);
    }
}
