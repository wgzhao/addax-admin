package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.audit.AuditRecorder;
import com.wgzhao.addax.admin.common.CalendarType;
import com.wgzhao.addax.admin.exception.ApiException;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.service.CalendarService;
import com.wgzhao.addax.admin.service.DictService;
import com.wgzhao.addax.admin.service.TradeCalendarService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 采集日历（交易日历 1021 / 工作日历 1022）的维护接口。
 *
 * <p>取代通用字典维护里对日历字典的裸编辑：日历在这里是一个有语义的日期集合，而不是
 * 一堆 key/value 行。维护动作（按年批量生成、交易日年度补全、单日增删）都落在这个类型化端点。</p>
 */
@RestController
@RequestMapping("/calendars")
@AllArgsConstructor
public class CalendarController
{
    private final DictService dictService;
    private final CalendarService calendarService;
    private final TradeCalendarService tradeCalendarService;
    private final AuditRecorder auditRecorder;

    public record CalendarCoverage(int code, String calendar, String lastDate, String warning)
    {
    }

    public record AddDateRequest(String date)
    {
    }

    /**
     * 每个日历字典最后覆盖到哪一天，以及是否需要提醒维护者导入后续日期。
     */
    @GetMapping("")
    public List<CalendarCoverage> coverage()
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
     * 列出某个日历字典下的日期（yyyyMMdd），可选按年份过滤。
     */
    @GetMapping("/{dictCode}/dates")
    public List<SysItem> listDates(@PathVariable int dictCode,
                                   @RequestParam(required = false) Integer year)
    {
        requireCalendarDict(dictCode);
        return dictService.listCalendarDates(dictCode, year);
    }

    /**
     * 手工新增一个日历日期（调休上班日、漏维护的交易日）。已存在则幂等跳过。
     */
    @PostMapping("/{dictCode}/dates")
    @Transactional
    public ResponseEntity<Void> addDate(@PathVariable int dictCode, @RequestBody AddDateRequest request)
    {
        requireCalendarDict(dictCode);
        String dateStr = validateDate(request == null ? null : request.date());
        boolean added = dictService.addCalendarDate(dictCode, dateStr);
        auditRecorder.success("CALENDAR_DATE_ADDED", "CONFIG", "WARNING", "CALENDAR",
            dictCode + ":" + dateStr, dateStr, null, null, Map.of("date", dateStr, "added", added));
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK).build();
    }

    /**
     * 删除一个日历日期（法定节假日）。不存在时静默忽略。
     */
    @DeleteMapping("/{dictCode}/dates/{date}")
    @Transactional
    public ResponseEntity<Void> removeDate(@PathVariable int dictCode, @PathVariable String date)
    {
        requireCalendarDict(dictCode);
        String dateStr = validateDate(date);
        boolean removed = dictService.removeCalendarDate(dictCode, dateStr);
        auditRecorder.success("CALENDAR_DATE_REMOVED", "CONFIG", "WARNING", "CALENDAR",
            dictCode + ":" + dateStr, dateStr, null, null, Map.of("date", dateStr, "removed", removed));
        return ResponseEntity.noContent().build();
    }

    /**
     * 生成指定年份的日历（交易日历 1021 / 工作日历 1022），includeWeekend 控制是否包含周末。
     */
    @PostMapping("/{dictCode}/generate/{year}/{includeWeekend}")
    @Transactional
    public ResponseEntity<Void> generate(@PathVariable int dictCode,
                                         @PathVariable int year,
                                         @PathVariable boolean includeWeekend)
    {
        requireCalendarDict(dictCode);
        if (year < 1900 || year > 9999) {
            throw new ApiException(400, "年份超出范围：" + year);
        }
        int added = dictService.generateCalendar(dictCode, year, includeWeekend);
        auditRecorder.success("CALENDAR_GENERATED", "CONFIG", "WARNING", "CALENDAR",
            dictCode, "calendar " + year, null, null,
            Map.of("year", year, "includeWeekend", includeWeekend, "added", added));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 年度补全交易日历（1021）：优先柜台接口，失败时退化为固定规则并在 warning 说明。
     */
    @PostMapping("/{dictCode}/refresh-trade/{year}")
    @Transactional
    public ResponseEntity<TradeCalendarService.RefreshResult> refreshTrade(@PathVariable int dictCode,
                                                                           @PathVariable int year)
    {
        if (!CalendarType.TRADE_DAY.getDictCode().equals(dictCode)) {
            throw new ApiException(400, "仅交易日历（" + CalendarType.TRADE_DAY.getDictCode() + "）支持年度补全");
        }
        if (year < 2000 || year > 2100) {
            throw new ApiException(400, "年份超出范围：" + year);
        }
        TradeCalendarService.RefreshResult result = tradeCalendarService.refresh(year);
        auditRecorder.success("TRADE_CALENDAR_REFRESHED", "CONFIG", "WARNING", "CALENDAR",
            dictCode, "trade calendar " + year, null, result, Map.of("year", year));
        return ResponseEntity.ok(result);
    }

    private void requireCalendarDict(int dictCode)
    {
        if (CalendarType.ofDictCode(dictCode).isEmpty()) {
            throw new ApiException(400, "字典 " + dictCode + " 不是日历字典");
        }
    }

    private String validateDate(String date)
    {
        if (date == null || date.isBlank()) {
            throw new ApiException(400, "缺少日期");
        }
        String trimmed = date.trim();
        try {
            LocalDate.parse(trimmed, com.wgzhao.addax.admin.common.Constants.shortSdf);
            return trimmed;
        }
        catch (DateTimeParseException e) {
            throw new ApiException(400, "日期格式应为 yyyyMMdd：" + trimmed);
        }
    }
}
