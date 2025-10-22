package com.project.demo.domain.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.dto.response.StockData;
import com.project.demo.domain.userstock.entity.UserStock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService{

    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    private static final int PRINCIPAL = 10000000; // 초기 원금

    /*
    내 포토폴리오 조회
     */
    public PortfolioResponse getMyPortfolio(Long userId) {
        Portfolio myPortfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(NotFoundPortfolioException::new);

        calculateReturnRate(myPortfolio);

        return PortfolioResponse.of(myPortfolio);
    }

    /*
    현재 내 포토폴리오의 수익률 계산
     */
    public void calculateReturnRate(Portfolio portfolio) {
        List<UserStock> userStocks = portfolio.getUserStocks();
        int stockAsset = 0; // 현재 보유하고 있는 주식 자산

        // 가지고 있는 종목들의 가격 합 계산
        for (UserStock userStock : userStocks) {
            String ticker = userStock.getTicker();
            int stockPrice = getStockPrice(ticker);
            int stockQuantity = userStock.getTotalQuantity();
            stockAsset += (stockPrice * stockQuantity);
        }

        // 총 자산 계산 (현금 + 보유 주식 총액)
        long totalCurrentAsset = portfolio.getBalance() + stockAsset;

        // 수익률 계산
        double returnRate;
        if (userStocks.isEmpty()) {
            // 주식을 전혀 보유하지 않은 경우: 초기 원금 대비 수익률 0% (초기 현금만 있는 상태)
            returnRate = ((double) (totalCurrentAsset - PRINCIPAL) / PRINCIPAL) * 100;
        } else {
            // 주식을 보유한 경우: 초기 원금 대비 총 자산 수익률
            returnRate = ((double) (totalCurrentAsset - PRINCIPAL) / PRINCIPAL) * 100;
        }

        // 포트폴리오 정보를 JSON으로 구성하여 실시간 업데이트
        Map<String, Object> portfolioUpdate = new HashMap<>();
        portfolioUpdate.put("returnRate", returnRate);
        portfolioUpdate.put("stockAsset", stockAsset);  // 보유 주식 총액
        portfolioUpdate.put("totalAsset", totalCurrentAsset);  // 총 자산 (현금 + 주식)
        portfolioUpdate.put("balance", portfolio.getBalance());  // 현금 잔액

        try {
            String portfolioJson = objectMapper.writeValueAsString(portfolioUpdate);
            redisTemplate.convertAndSend("portfolio:updates", portfolioJson);
        } catch (Exception e) {
            log.error("포트폴리오 업데이트 JSON 변환 오류", e);
            // JSON 변환 실패 시 수익률만 전송
            redisTemplate.convertAndSend("portfolio:updates", String.valueOf(returnRate));
        }
    }


    /*
    Redis에서 주식 실시간 현재가 꺼내기
     */
    public int getStockPrice(String ticker) {
        String key = "stock:data:" + ticker;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) throw new IllegalStateException("데이터 없음");

        try {
            StockData data = objectMapper.readValue(json, StockData.class);
            return data.getPrice();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    모든 사용자의 포트폴리오 수익률을 주기적으로 계산하고 실시간 발행
    */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void updateAllPortfolioReturnRates() {
        try {
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            
            for (Portfolio portfolio : allPortfolios) {
                try {
                    calculateReturnRate(portfolio);
                } catch (Exception e) {
                    log.warn("포트폴리오 수익률 계산 실패 (사용자 ID: {}): {}", 
                            portfolio.getUser().getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("포트폴리오 수익률 업데이트 스케줄러 오류", e);
        }
    }
}
