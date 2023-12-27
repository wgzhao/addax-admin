package com.wgzhao.fsbrowser.utils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

public class TradeDateUtils {


    public static String calcTradeDate(Integer shiftDay, String dateFormat) {
        Date day = calcTradeDate(shiftDay);
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        return sdf.format(day);
    }

    public static Date calcTradeDate(Integer shiftDay) {
        LocalDate startingDate = LocalDate.now();
        // calc 5 work day ago
        LocalDate day = startingDate.minusDays(shiftDay);  // Add 5 workdays (excluding weekends)
        while (day.getDayOfWeek().getValue() > 5) {
            day = day.minusDays(1);
        }
        return new Date(day.toEpochDay() * 86400 * 1000);
    }
}
