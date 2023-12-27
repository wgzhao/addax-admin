package com.wgzhao.fsbrowser.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

public class TradeDateUtils {

    public static String calcTradeDate(Integer shiftDay) {
        return calcTradeDate(shiftDay, "yyyyMMdd");
    }

    public static String calcTradeDate(Integer shiftDay, String dateFormat) {
        LocalDate startingDate = LocalDate.now();
        // calc 5 work day ago
        LocalDate day = startingDate.minusDays(shiftDay);  // Add 5 workdays (excluding weekends)
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.minusDays(1);
        }
        DateTimeFormatter sf = DateTimeFormatter.ofPattern(dateFormat);
        return day.format(sf);
    }
}
