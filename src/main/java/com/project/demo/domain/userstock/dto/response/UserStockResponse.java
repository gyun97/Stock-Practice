package com.project.demo.domain.userstock.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserStockResponse {
    
    private String ticker;
    private String companyName;
    private int totalQuantity;
    private int avgPrice;
    private int currentPrice;
    private long currentAsset;
    private long profitLoss;
    private double returnRate;
    
    public static UserStockResponse of(String ticker, String companyName, int totalQuantity, 
                                     int avgPrice, int currentPrice) {
        long currentAsset = (long) currentPrice * totalQuantity;
        long profitLoss = currentAsset - ((long) avgPrice * totalQuantity);
        double returnRate = avgPrice > 0 ? ((double) profitLoss / ((long) avgPrice * totalQuantity)) * 100 : 0.0;
        
        return UserStockResponse.builder()
                .ticker(ticker)
                .companyName(companyName)
                .totalQuantity(totalQuantity)
                .avgPrice(avgPrice)
                .currentPrice(currentPrice)
                .currentAsset(currentAsset)
                .profitLoss(profitLoss)
                .returnRate(returnRate)
                .build();
    }
}