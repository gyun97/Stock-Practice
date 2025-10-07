package com.project.demo.domain.stock.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockResponse {

    private String ticker; // 종목 코드
    private int price; // 가격
    private double changeAmount; // 주가 변화량
    private double changeRate; // 등락률
    private String companyName; // 회사 이름
    private String tradeTime; // 체결 시간
    private long volume; // 누적 거래량
}
