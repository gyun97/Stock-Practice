package com.project.demo.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateUtil 단위 테스트
 */
class DateUtilTest {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Test
    void 오늘_날짜_테스트() {
        // Given & When
        String result = DateUtil.today();
        String expected = LocalDate.now().format(FORMATTER);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals(8, result.length()); // yyyyMMdd 형식
    }

    @Test
    void n개월_전_날짜_테스트() {
        // Given
        int months = 3;

        // When
        String result = DateUtil.monthsAgo(months);
        String expected = LocalDate.now().minusMonths(months).format(FORMATTER);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals(8, result.length());
    }

    @Test
    void n일_전_날짜_테스트() {
        // Given
        int days = 7;

        // When
        String result = DateUtil.daysAgo(days);
        String expected = LocalDate.now().minusDays(days).format(FORMATTER);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals(8, result.length());
    }

    @Test
    void n년_전_날짜_테스트() {
        // Given
        int years = 1;

        // When
        String result = DateUtil.yearsAgo(years);
        String expected = LocalDate.now().minusYears(years).format(FORMATTER);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals(8, result.length());
    }

    @Test
    void n주_전_날짜_테스트() {
        // Given
        int weeks = 2;

        // When
        String result = DateUtil.weeksAgo(weeks);
        String expected = LocalDate.now().minusWeeks(weeks).format(FORMATTER);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals(8, result.length());
    }

    @Test
    void 날짜_형식_검증_테스트() {
        // Given & When
        String today = DateUtil.today();

        // Then
        assertTrue(today.matches("\\d{8}"), "날짜는 8자리 숫자여야 합니다");
        
        // 날짜 파싱 가능 여부 확인
        LocalDate parsedDate = LocalDate.parse(today, FORMATTER);
        assertNotNull(parsedDate);
    }

    @Test
    void 여러_날짜_계산_일관성_테스트() {
        // Given
        LocalDate baseDate = LocalDate.now();

        // When
        String daysAgo7 = DateUtil.daysAgo(7);
        String weeksAgo1 = DateUtil.weeksAgo(1);

        // Then
        LocalDate expectedDaysAgo = baseDate.minusDays(7);
        LocalDate expectedWeeksAgo = baseDate.minusWeeks(1);
        
        assertEquals(expectedDaysAgo.format(FORMATTER), daysAgo7);
        assertEquals(expectedWeeksAgo.format(FORMATTER), weeksAgo1);
    }
}


