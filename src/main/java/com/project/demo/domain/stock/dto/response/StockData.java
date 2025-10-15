package com.project.demo.domain.stock.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockData {
    private String ticker;
    private int price;
    private int changeAmount;
    private double changeRate;
    private String companyName;
    private String tradeTime;
    private long volume;
}
