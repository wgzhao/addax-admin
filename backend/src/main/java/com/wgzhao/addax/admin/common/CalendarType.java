package com.wgzhao.addax.admin.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * Collection calendar: which dates a source is allowed to collect on.
 *
 * <p>{@link #NATURAL_DAY} is the default and needs no data at all — every date belongs to it, so
 * maintaining 365 rows a year for it would be pure cost. The other kinds are backed by a dictionary
 * whose {@code item_key} values are the {@code yyyyMMdd} dates that belong to the calendar. The row set
 * <em>is</em> the calendar: adding an attribute column to encode two calendars in one dictionary would
 * silently change the meaning of every existing query that reads that dictionary as a set.</p>
 */
public enum CalendarType
{
    NATURAL_DAY(null),
    WORK_DAY(1022),
    TRADE_DAY(1021);

    private final Integer dictCode;

    CalendarType(Integer dictCode)
    {
        this.dictCode = dictCode;
    }

    public Integer getDictCode()
    {
        return dictCode;
    }

    public boolean isDictBacked()
    {
        return dictCode != null;
    }

    /**
     * Parse a stored or requested value. Blank means "use the default"; anything else must name a
     * known calendar, otherwise the caller has to reject it — a typo must not silently degrade into
     * the default, because that is the difference between collecting on holidays and not.
     */
    public static Optional<CalendarType> parse(String value)
    {
        if (value == null || value.isBlank()) {
            return Optional.of(NATURAL_DAY);
        }
        String trimmed = value.trim();
        for (CalendarType type : values()) {
            if (type.name().equalsIgnoreCase(trimmed)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Runtime resolution for values already stored in the database. Falls back to the default instead of
     * throwing: a bad row must not stop the scheduler from running, and collecting one extra day is far
     * cheaper to recover from than collecting nothing at all. Callers log when they see a fallback.
     */
    public static CalendarType resolve(String value)
    {
        return parse(value).orElse(NATURAL_DAY);
    }

    /**
     * The calendar backed by a dictionary code, if the code belongs to one.
     */
    public static Optional<CalendarType> ofDictCode(int dictCode)
    {
        return Arrays.stream(values())
            .filter(type -> type.dictCode != null && type.dictCode == dictCode)
            .findFirst();
    }
}
