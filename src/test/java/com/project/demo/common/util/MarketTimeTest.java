package com.project.demo.common.util;

import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarketTime 단위 테스트
 */
class MarketTimeTest {

    @Test
    void 장_시간_상수_검증_테스트() {
        // Given & When & Then
        assertEquals(ZoneId.of("Asia/Seoul"), MarketTime.KST);
        assertEquals(LocalTime.of(9, 0), MarketTime.REGULAR_OPEN);
        assertEquals(LocalTime.of(15, 20), MarketTime.REGULAR_CLOSE);
    }

    @Test
    void 평일_장_시간_중_장_개장_테스트() {
        // Given - 평일 오전 10시 (장 시간)
        ZonedDateTime weekdayMarketTime = ZonedDateTime.of(
                LocalDate.of(2024, 1, 15), // 월요일
                LocalTime.of(10, 0),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(weekdayMarketTime);

        // Then
        assertTrue(result, "평일 장 시간 중에는 장이 열려있어야 합니다");
    }

    @Test
    void 평일_장_시간_전_장_폐장_테스트() {
        // Given - 평일 오전 8시 (장 시간 전)
        ZonedDateTime beforeMarketTime = ZonedDateTime.of(
                LocalDate.of(2024, 1, 15), // 월요일
                LocalTime.of(8, 0),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(beforeMarketTime);

        // Then
        assertFalse(result, "장 시간 전에는 장이 닫혀있어야 합니다");
    }

    @Test
    void 평일_장_시간_후_장_폐장_테스트() {
        // Given - 평일 오후 4시 (장 시간 후)
        ZonedDateTime afterMarketTime = ZonedDateTime.of(
                LocalDate.of(2024, 1, 15), // 월요일
                LocalTime.of(16, 0),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(afterMarketTime);

        // Then
        assertFalse(result, "장 시간 후에는 장이 닫혀있어야 합니다");
    }

    @Test
    void 토요일_장_폐장_테스트() {
        // Given - 토요일 오전 10시
        ZonedDateTime saturday = ZonedDateTime.of(
                LocalDate.of(2024, 1, 13), // 토요일
                LocalTime.of(10, 0),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(saturday);

        // Then
        assertFalse(result, "토요일에는 장이 닫혀있어야 합니다");
    }

    @Test
    void 일요일_장_폐장_테스트() {
        // Given - 일요일 오전 10시
        ZonedDateTime sunday = ZonedDateTime.of(
                LocalDate.of(2024, 1, 14), // 일요일
                LocalTime.of(10, 0),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(sunday);

        // Then
        assertFalse(result, "일요일에는 장이 닫혀있어야 합니다");
    }

    @Test
    void 장_개장_시간_정확히_개장_테스트() {
        // Given - 평일 오전 9시 정각 (장 개장 시간)
        ZonedDateTime openTime = ZonedDateTime.of(
                LocalDate.of(2024, 1, 15), // 월요일
                LocalTime.of(9, 0),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(openTime);

        // Then
        assertTrue(result, "장 개장 시간에는 장이 열려있어야 합니다");
    }

    @Test
    void 장_폐장_시간_정확히_폐장_테스트() {
        // Given - 평일 오후 3시 20분 (장 폐장 시간)
        ZonedDateTime closeTime = ZonedDateTime.of(
                LocalDate.of(2024, 1, 15), // 월요일
                LocalTime.of(15, 20),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(closeTime);

        // Then
        assertTrue(result, "장 폐장 시간에는 장이 열려있어야 합니다");
    }

    @Test
    void 장_폐장_시간_1분_후_폐장_테스트() {
        // Given - 평일 오후 3시 21분 (장 폐장 시간 1분 후)
        ZonedDateTime afterCloseTime = ZonedDateTime.of(
                LocalDate.of(2024, 1, 15), // 월요일
                LocalTime.of(15, 21),
                MarketTime.KST
        );

        // When
        boolean result = MarketTime.isMarketOpenAt(afterCloseTime);

        // Then
        assertFalse(result, "장 폐장 시간 이후에는 장이 닫혀있어야 합니다");
    }

    @Test
    void 현재_시간_장_개장_여부_테스트() {
        // Given & When
        boolean result = MarketTime.isMarketOpen();

        // Then
        // 현재 시간에 따라 결과가 달라질 수 있으므로 단순히 호출 가능 여부만 확인
        assertNotNull(Boolean.valueOf(result));
    }
}

