package com.wgzhao.addax.admin.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具类
 */
public class DateUtils {

        /**
         * 获取当前日期时间
         * @return 格式化的日期时间字符串 (yyyy-MM-dd HH:mm:ss)
         */
        public static String getCurrentDateTime() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        /**
         * 获取当前日期时间，使用指定格式
         * @param pattern 日期格式
         * @return 格式化的日期时间字符串
         */
        public static String getCurrentDateTime(String pattern) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
        }

        /**
         * 获取当前日期
         * @param pattern 日期格式
         * @return 格式化的日期字符串
         */
        public static String getCurrentDate(String pattern) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));
        }

        /**
         * 获取昨天的日期
         * @param pattern 日期格式
         * @return 格式化的日期字符串
         */
        public static String getYesterdayDate(String pattern) {
            return LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern(pattern));
        }
}