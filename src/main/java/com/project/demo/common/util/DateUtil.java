package com.project.demo.common.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 오늘 날짜 (yyyyMMdd)
    public static String today() {
        return LocalDate.now().format(FORMATTER);
    }

//    // 최근 거래일 날짜 (주말/공휴일 제외)
//    public static String getLatestTradingDate() {
//        LocalDate today = LocalDate.now();
//
//        // 오늘이 주말이면 금요일로 조정
//        while (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
//            today = today.minusDays(1);
//        }
//
//        // TODO: 공휴일 체크 로직 추가 가능
//        // 현재는 주말만 고려
//
//        return today.format(FORMATTER);
//    }

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
