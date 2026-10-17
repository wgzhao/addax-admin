package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.common.CalendarType;
import com.wgzhao.addax.admin.repository.SysItemRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers the two calendar questions the collection pipeline asks:
 * "may this source collect on this date" and "which business date does a run belong to".
 *
 * <p>Both the trigger gate and the business-date derivation go through here so a source cannot be
 * gated on one calendar and have its data stamped with another. The calendars live in the dictionary
 * ({@link CalendarType} maps a name to its dict code) and hold plain date sets.</p>
 *
 * <p>Lookups are memoized for a minute. Calendar data changes at most a few times a year, so a minute
 * of staleness after an edit is not worth an invalidation protocol; a longer window would delay the
 * moment a drained calendar starts collecting again.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService
{
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String EXPIRED_ALERT_KEY_PREFIX = "etl:calendar:expired:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    private static final int CACHE_PRUNE_THRESHOLD = 256;
    /** How far ahead a maintainer is warned that a calendar is about to run out of data. */
    private static final int EXPIRY_WARN_DAYS = 30;

    private final SysItemRepo sysItemRepo;
    private final DictService dictService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AlertService alertService;

    private final Map<String, CachedDate> lastDateCache = new ConcurrentHashMap<>();
    private final Map<Integer, CachedDate> maxDateCache = new ConcurrentHashMap<>();

    public enum Reason
    {
        NATURAL_DAY,
        BUSINESS_DAY,
        NOT_BUSINESS_DAY,
        CALENDAR_EXPIRED
    }

    public record Verdict(boolean due, Reason reason)
    {
    }

    /**
     * Verdict for "may a source using {@code calendar} collect on {@code date}".
     *
     * <p>A date missing from the calendar and a calendar that has run out of data both refuse, but they
     * are reported separately: the first is routine (a weekend), the second is an operational failure
     * that stops every source using that calendar.</p>
     */
    public Verdict evaluate(String calendar, LocalDate date)
    {
        CalendarType type = resolve(calendar);
        if (!type.isDictBacked()) {
            return new Verdict(true, Reason.NATURAL_DAY);
        }
        if (isExpired(type, date)) {
            return new Verdict(false, Reason.CALENDAR_EXPIRED);
        }
        boolean member = lastDateOnOrBefore(type, date).filter(date::equals).isPresent();
        return new Verdict(member, member ? Reason.BUSINESS_DAY : Reason.NOT_BUSINESS_DAY);
    }

    public boolean isBusinessDay(String calendar, LocalDate date)
    {
        return evaluate(calendar, date).due();
    }

    /**
     * Business date a run started right now belongs to, for the calendar a source is configured with.
     */
    public LocalDate businessDate(String calendar)
    {
        return businessDate(resolve(calendar), dictService.getSwitchTimeAsTime(), LocalDate.now(), LocalTime.now());
    }

    /**
     * Calendar date of the business period that is open right now, i.e. the date a trigger has to be
     * judged against. The switch time opens a new period, so a trigger before it (early morning) still
     * belongs to the previous calendar day: 01:30 on Saturday collects Friday's trading data, and the
     * calendar has to allow Friday, not Saturday.
     *
     * <p>Do not confuse this with {@link #businessDate(String)}: that one returns the latest calendar
     * business date before the cursor, which on a weekend morning is the previous trading day even though
     * the period now open is the rest day itself.</p>
     */
    public LocalDate businessPeriodDate()
    {
        return businessPeriodDate(LocalDate.now(), LocalTime.now());
    }

    /**
     * Same rule for callers that already hold the minute they are processing (the table-override tick).
     */
    public LocalDate businessPeriodDate(LocalDate today, LocalTime now)
    {
        return now.isBefore(dictService.getSwitchTimeAsTime()) ? today.minusDays(1) : today;
    }

    /**
     * Cursor rule shared with the historical {@code getBizDate()}: once the switch time has passed the
     * cursor moves to tomorrow, so the result is the latest calendar date strictly before it. After the
     * switch a run therefore carries today's date, before the switch the previous business date.
     */
    LocalDate businessDate(CalendarType type, LocalTime switchTime, LocalDate today, LocalTime now)
    {
        LocalDate cursor = now.isAfter(switchTime) && now.isBefore(LocalTime.of(23, 59))
            ? today.plusDays(1)
            : today;
        return previousBusinessDay(type, cursor);
    }

    public LocalDate previousBusinessDay(String calendar, LocalDate date)
    {
        return previousBusinessDay(resolve(calendar), date);
    }

    private LocalDate previousBusinessDay(CalendarType type, LocalDate date)
    {
        if (!type.isDictBacked()) {
            return date.minusDays(1);
        }
        // Fail open on a drained or unreadable calendar: every caller of this method is deriving a date
        // for data that is about to be written, and the natural previous day keeps the pipeline moving
        // while the refusal alert (raised by evaluate) tells the maintainer to refill the calendar.
        return lastDateOnOrBefore(type, date.minusDays(1)).orElseGet(() -> {
            log.warn("日历 {} 在 {} 之前没有任何日期，业务日期退化为自然日", type, date);
            return date.minusDays(1);
        });
    }

    /**
     * Last date a calendar covers, so callers can warn before it runs out.
     */
    public Optional<LocalDate> lastCoveredDate(String calendar)
    {
        CalendarType type = resolve(calendar);
        if (!type.isDictBacked()) {
            return Optional.empty();
        }
        return maxDate(type);
    }

    /**
     * The dates a calendar expects to be collected inside {@code [from, to]}. Monitoring compares
     * collected dates against this set, so a workday or trading-day source is not reported missing on
     * the rest days it is deliberately skipping.
     */
    public List<LocalDate> businessDatesInWindow(String calendar, LocalDate from, LocalDate to)
    {
        CalendarType type = resolve(calendar);
        if (to.isBefore(from)) {
            return List.of();
        }
        if (!type.isDictBacked()) {
            return from.datesUntil(to.plusDays(1)).toList();
        }
        return sysItemRepo.findCalendarDatesBetween(type.getDictCode(), from.format(DATE_FMT), to.format(DATE_FMT))
            .stream()
            .map(CalendarService::parseDate)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Log a refusal and, when a calendar has run out of data, alert once per calendar per day. The tick
     * that produced the refusal runs every minute, so without the marker a drained calendar would page
     * the maintainer sixty times an hour.
     */
    public void reportRefusal(Verdict verdict, String calendar, String context)
    {
        switch (verdict.reason()) {
            case CALENDAR_EXPIRED -> {
                log.warn("拒绝采集：日历 {} 已耗尽覆盖范围，{}", calendar, context);
                alertExpiredOnce(calendar);
            }
            case NOT_BUSINESS_DAY -> log.info("日历 {} 中 {} 不是业务日，跳过采集：{}",
                calendar, LocalDate.now(), context);
            default -> {
            }
        }
    }

    /** Warn ahead of time so the yearly calendar import does not turn into a silent outage. */
    public Optional<String> coverageWarning(String calendar)
    {
        CalendarType type = resolve(calendar);
        if (!type.isDictBacked()) {
            return Optional.empty();
        }
        return maxDate(type).flatMap(max -> {
            LocalDate warnFrom = LocalDate.now().plusDays(EXPIRY_WARN_DAYS);
            if (!max.isBefore(warnFrom)) {
                return Optional.empty();
            }
            return Optional.of(max.isBefore(LocalDate.now())
                ? String.format("%s 日历仅覆盖到 %s，已经过期，使用该日历的采集已停止", calendar, max)
                : String.format("%s 日历仅覆盖到 %s，请及时导入后续日期", calendar, max));
        });
    }

    private boolean isExpired(CalendarType type, LocalDate date)
    {
        return maxDate(type).map(max -> max.isBefore(date)).orElse(true);
    }

    private Optional<LocalDate> maxDate(CalendarType type)
    {
        CachedDate cached = maxDateCache.get(type.getDictCode());
        if (cached != null && !cached.isStale()) {
            return Optional.ofNullable(cached.value());
        }
        LocalDate value;
        try {
            value = parseDate(sysItemRepo.findMaxCalendarDate(type.getDictCode()));
        }
        catch (Exception e) {
            log.error("读取日历 {} 覆盖范围失败", type, e);
            return Optional.empty();
        }
        maxDateCache.put(type.getDictCode(), CachedDate.now(value));
        return Optional.ofNullable(value);
    }

    /**
     * Latest calendar date at or before {@code date}. Empty when the calendar has no such date.
     */
    private Optional<LocalDate> lastDateOnOrBefore(CalendarType type, LocalDate date)
    {
        if (!type.isDictBacked()) {
            return Optional.of(date);
        }
        String key = type.name() + '|' + date.format(DATE_FMT);
        CachedDate cached = lastDateCache.get(key);
        if (cached != null && !cached.isStale()) {
            return Optional.ofNullable(cached.value());
        }
        LocalDate value;
        try {
            value = parseDate(sysItemRepo.findLastCalendarDateBefore(
                type.getDictCode(), date.plusDays(1).format(DATE_FMT)));
        }
        catch (Exception e) {
            log.error("读取日历 {} 的最近业务日失败", type, e);
            return Optional.empty();
        }
        if (lastDateCache.size() > CACHE_PRUNE_THRESHOLD) {
            lastDateCache.entrySet().removeIf(entry -> entry.getValue().isStale());
        }
        lastDateCache.put(key, CachedDate.now(value));
        return Optional.ofNullable(value);
    }

    private void alertExpiredOnce(String calendar)
    {
        String key = EXPIRED_ALERT_KEY_PREFIX + calendar + ':' + LocalDate.now().format(DATE_FMT);
        try {
            Boolean fresh = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofDays(2));
            if (!Boolean.TRUE.equals(fresh)) {
                return;
            }
        }
        catch (Exception e) {
            log.warn("写入日历过期告警标记失败，本次仍发送告警", e);
        }
        alertService.sendToWeComRobot(String.format(
            "采集日历 %s 已经耗尽覆盖范围（最后日期 %s），所有使用该日历的源已停止采集，请导入后续日期。",
            calendar, maxDate(resolve(calendar)).map(LocalDate::toString).orElse("未知")));
    }

    private CalendarType resolve(String calendar)
    {
        return CalendarType.parse(calendar).orElseGet(() -> {
            log.warn("未知的采集日历配置 '{}'，按自然日处理", calendar);
            return CalendarType.NATURAL_DAY;
        });
    }

    private static LocalDate parseDate(String raw)
    {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DATE_FMT);
        }
        catch (Exception e) {
            log.warn("日历日期格式非法，已忽略：{}", raw);
            return null;
        }
    }

    private record CachedDate(LocalDate value, long loadedAtNanos)
    {
        static CachedDate now(LocalDate value)
        {
            return new CachedDate(value, System.nanoTime());
        }

        boolean isStale()
        {
            return System.nanoTime() - loadedAtNanos > CACHE_TTL.toNanos();
        }
    }
}
