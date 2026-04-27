package com.project.demo.domain.stock.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KospiResponse {
    private double currentIndex;  // 현재 지수값
    private double change;        // 전일 대비 변화량
    private double changeRate;    // 전일 대비 등락률 (%)
    private double open;          // 시가
    private double high;          // 고가
    private double low;           // 저가
    private double prevClose;     // 전일 종가
    private double high52w;       // 52주 최고
    private double low52w;        // 52주 최저
    private String baseDate;      // 기준 날짜 (YYYYMMDD)
}
