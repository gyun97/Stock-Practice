package com.project.demo.domain.portfolio.dto.response;

import com.project.demo.domain.portfolio.entity.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {

    private long balance; // 현금 잔액(가용 자산)
    private long stockAsset; // 보유 주식 총액
    private long totalAsset; // 총 자산
    private int holdCount; // 보유 종목 수
    private int totalQuantity; // 보유 주식 수량
    private double returnRate; // 수익률

    public static PortfolioResponse of(Portfolio portfolio) {
        return new PortfolioResponse(
                portfolio.getBalance(),
                0, // stockAsset은 calculateReturnRate에서 계산
                0, // totalAsset은 calculateReturnRate에서 계산
                portfolio.getHoldCount(),
                portfolio.getTotalQuantity(),
                0.0 // returnRate는 calculateReturnRate에서 계산
        );
    }
    
    public static PortfolioResponse of(Portfolio portfolio, long stockAsset, long totalAsset, double returnRate) {
        return new PortfolioResponse(
                portfolio.getBalance(),
                stockAsset,
                totalAsset,
                portfolio.getHoldCount(),
                portfolio.getTotalQuantity(),
                returnRate
        );
    }
}
