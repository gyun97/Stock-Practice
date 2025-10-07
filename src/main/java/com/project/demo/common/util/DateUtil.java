package com.project.demo.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 오늘 날짜 (yyyyMMdd)
    public static String today() {
        return LocalDate.now().format(FORMATTER);
    }

    // n개월 전 날짜 (yyyyMMdd)
    public static String monthsAgo(int months) {
        return LocalDate.now().minusMonths(months).format(FORMATTER);
    }

    // n일 전 날짜 (yyyyMMdd)
    public static String daysAgo(int days) {
        return LocalDate.now().minusDays(days).format(FORMATTER);
    }

    // n년 전 날짜 (yyyyMMdd)
    public static String yearsAgo(int years) {
        return LocalDate.now().minusYears(years).format(FORMATTER);
    }

    // n주 전 날짜 (yyyyMMdd)
    public static String weeksAgo(int weeks) {
        return LocalDate.now().minusWeeks(weeks).format(FORMATTER);
    }


}
