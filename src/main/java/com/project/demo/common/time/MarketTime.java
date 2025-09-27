package com.project.demo.common.time;

import java.time.*;

public class MarketTime {

    // === 장 시간 계산 로직 ===
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final LocalTime REGULAR_OPEN  = LocalTime.of(9, 0);
    public static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);

    public static boolean isMarketOpen() {
        return isMarketOpenAt(ZonedDateTime.now(KST));
    }

    public static boolean isMarketOpenAt(ZonedDateTime when) {
        boolean isWeekday = isWeekday(when.toLocalDate());
        boolean inRegularHours = isInRegularSession(when.toLocalTime());
        boolean isHoliday = isKrxHoliday(when.toLocalDate());
        return isWeekday && !isHoliday && inRegularHours;
    }

    private static boolean isWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    private static boolean isInRegularSession(LocalTime time) {
        return !time.isBefore(REGULAR_OPEN) && !time.isAfter(REGULAR_CLOSE);
    }

    private static boolean isKrxHoliday(LocalDate date) {
        return false; // 추후 확장 가능
    }
}
