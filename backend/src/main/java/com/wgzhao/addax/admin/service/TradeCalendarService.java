package com.wgzhao.addax.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.common.CalendarType;
import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 交易日历（字典 1021）的年度补全。
 *
 * <p>交易日历是唯一能回答"某天能不能采"的数据，缺日会让使用它的源静默停采、多日会让它在节假日空跑，
 * 所以补全必须可重复、可解释：</p>
 *
 * <ul>
 *   <li>优先从柜台接口取 SSE 休市日，再用"工作日 − 休市日"得到交易日；请求带连接与整体超时，响应结构
 *       变化、年份对不上或休市日数量异常都按"取不到"处理，而不是把错误数据写进日历。</li>
 *   <li>取不到时给出明确提示，并退化为固定规则：周末不计，元旦 1 月 1-3 日、国庆 10 月 1-7 日休市。
 *       交易日历不考虑周末调休，所以不会把任何周末加回来。</li>
 *   <li>官方数据是权威，可以对这一年做增删（节假日被移除，否则门禁会放行）；退化数据只是近似，
 *       只增不删——否则一次接口故障就会把人工维护过的节假日抹掉。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeCalendarService
{
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * A year always has more weekday holidays than this; fewer means the response was empty (the next
     * year is usually published late) or the payload changed shape.
     */
    private static final int MIN_REMOTE_REST_DAYS = 5;

    private final SysItemRepo sysItemRepo;
    private final ObjectMapper objectMapper;

    @Value("${calendar.trade.url:https://www.ciis.com.hk/hongkong/transactionCalendar/queryTransactionCalendar.do}")
    private String sourceUrl;

    @Value("${calendar.trade.timeout-ms:15000}")
    private long timeoutMs;

    /**
     * @param source         SSE（官方接口）或 FALLBACK（固定规则）
     * @param warning        退化时的提示；官方数据成功时为 null
     * @param removedDates   被移除的日期（只可能来自官方数据）
     */
    public record RefreshResult(int year, String source, int tradingDays, int added, int removed,
                                List<String> removedDates, String warning)
    {
    }

    @Transactional
    public RefreshResult refresh(int year)
    {
        String source = "SSE";
        String warning = null;
        Set<LocalDate> trading;
        try
        {
            trading = remoteTradingDays(year);
        }
        catch (Exception e)
        {
            source = "FALLBACK";
            warning = "无法获取 " + year + " 年交易日历（" + reasonOf(e) + "），已按默认规则补全："
                + "不含周六日，并扣除元旦 1 月 1-3 日、国庆 10 月 1-7 日。"
                + "该规则不含春节等其它节假日，请人工核对后维护。";
            log.warn("{}", warning, e);
            trading = fallbackTradingDays(year);
        }

        Map<String, SysItem> existing = existingOf(year);
        List<String> removed = new ArrayList<>();
        if ("SSE".equals(source))
        {
            // The official list is authoritative for this year: a date it marks as non-trading must leave
            // the calendar, otherwise the gate would let a holiday through.
            for (Map.Entry<String, SysItem> entry : existing.entrySet())
            {
                LocalDate date = parseKey(entry.getKey());
                if (date != null && !trading.contains(date))
                {
                    removed.add(date.toString());
                    sysItemRepo.delete(entry.getValue());
                }
            }
        }

        int added = 0;
        for (LocalDate date : trading)
        {
            String key = date.format(SHORT);
            if (existing.containsKey(key))
            {
                continue;
            }
            SysItem item = new SysItem();
            item.setDictCode(CalendarType.TRADE_DAY.getDictCode());
            item.setItemKey(key);
            item.setItemValue(key);
            item.setRemark("SSE".equals(source) ? "柜台交易日历" : "默认规则(接口不可用)");
            sysItemRepo.save(item);
            added++;
        }
        removed.sort(String::compareTo);
        log.info("交易日历 {} 年补全完成：来源={} 交易日={} 新增={} 移除={}（该年原有 {} 条）", year, source,
            trading.size(), added, removed.size(), existing.size());
        return new RefreshResult(year, source, trading.size(), added, removed.size(), removed, warning);
    }

    /**
     * Fixed rule used when the official list cannot be read: weekdays only, minus the two holiday blocks
     * that never move (元旦 1 月 1-3 日、国庆 10 月 1-7 日). Weekend make-up workdays are irrelevant here —
     * the exchange does not trade on weekends even when offices work.
     */
    Set<LocalDate> fallbackTradingDays(int year)
    {
        Set<LocalDate> days = weekdays(year);
        for (int day = 1; day <= 3; day++)
        {
            days.remove(LocalDate.of(year, 1, day));
        }
        for (int day = 1; day <= 7; day++)
        {
            days.remove(LocalDate.of(year, 10, day));
        }
        return days;
    }

    private Set<LocalDate> weekdays(int year)
    {
        Set<LocalDate> days = new LinkedHashSet<>();
        for (LocalDate date = LocalDate.of(year, 1, 1); date.getYear() == year; date = date.plusDays(1))
        {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY)
            {
                days.add(date);
            }
        }
        return days;
    }

    private Set<LocalDate> remoteTradingDays(int year) throws Exception
    {
        URI uri = URI.create(sourceUrl);
        String origin = uri.getScheme() + "://" + uri.getHost();
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.min(timeoutMs, 10_000)))
            .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Origin", origin)
            .header("Referer", origin + "/")
            .header("User-Agent", "addax-admin trade-calendar refresh")
            .POST(HttpRequest.BodyPublishers.ofString("year=" + year + "-01"))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
        {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return parseRemote(response.body(), year);
    }

    /**
     * 官方响应形如 {@code {"transactionCalendars":[[沪港通...],[SSE...]]}}，每个条目带 marketType 与
     * restDate。只采信 SSE 的休市日；结构或年份不符时抛错，交给上层退化。
     */
    Set<LocalDate> parseRemote(String body, int year) throws Exception
    {
        JsonNode root = objectMapper.readTree(body);
        JsonNode groups = root.path("transactionCalendars");
        if (!groups.isArray())
        {
            throw new IllegalStateException("响应结构已变化（缺少 transactionCalendars）");
        }
        Set<LocalDate> rest = new LinkedHashSet<>();
        for (JsonNode group : groups)
        {
            if (!group.isArray())
            {
                continue;
            }
            for (JsonNode entry : group)
            {
                if (!"SSE".equals(entry.path("marketType").asText()))
                {
                    continue;
                }
                String raw = entry.path("restDate").asText("");
                LocalDate date = parseOrNull(raw);
                if (date == null)
                {
                    throw new IllegalStateException("无法解析的休市日：" + raw);
                }
                if (date.getYear() == year)
                {
                    rest.add(date);
                }
            }
        }
        if (rest.size() < MIN_REMOTE_REST_DAYS)
        {
            throw new IllegalStateException("未返回 " + year + " 年交易日历（SSE 休市日仅 " + rest.size() + " 条）");
        }
        Set<LocalDate> trading = weekdays(year);
        trading.removeAll(rest);
        return trading;
    }

    private Map<String, SysItem> existingOf(int year)
    {
        Map<String, SysItem> rows = new HashMap<>();
        String prefix = String.valueOf(year);
        for (SysItem item : sysItemRepo.findByDictCode(CalendarType.TRADE_DAY.getDictCode()))
        {
            if (item.getItemKey() != null && item.getItemKey().startsWith(prefix))
            {
                rows.put(item.getItemKey(), item);
            }
        }
        return rows;
    }

    /**
     * 字典里存的是 {@code yyyyMMdd}（{@link #SHORT}）；接口返回的是 ISO 日期，两种都要能读。
     */
    private static LocalDate parseKey(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.BASIC_ISO_DATE);
        }
        catch (RuntimeException e)
        {
            return parseOrNull(raw);
        }
    }

    private static LocalDate parseOrNull(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }
        try
        {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static String reasonOf(Exception e)
    {
        String message = e.getMessage();
        if (message == null || message.isBlank())
        {
            return e.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) + "…" : message;
    }
}
