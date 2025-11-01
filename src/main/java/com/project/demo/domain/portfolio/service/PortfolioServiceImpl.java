package com.project.demo.domain.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.common.websocket.WebSocketSessionManager;
import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.dto.response.StockData;
import com.project.demo.domain.userstock.dto.response.UserStockResponse;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.service.UserStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService{

    private final PortfolioRepository portfolioRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserStockService userStockService;
    private final WebSocketSessionManager sessionManager;

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
    현재 내 포토폴리오의 수익률 계산 (Redis 캐싱 적용)
     */
    public void calculateReturnRate(Portfolio portfolio) {
        Long userId = portfolio.getUser().getId();
        String cacheKey = "portfolio:data:" + userId;
        
        // Redis에서 캐시된 포트폴리오 데이터 조회
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        Map<String, Object> cachedData = null;
        
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(cachedJson, Map.class);
                cachedData = parsed;
            } catch (JsonProcessingException e) {
                log.warn("캐시된 포트폴리오 데이터 파싱 실패 (사용자 ID: {}): {}", userId, e.getMessage());
            }
        }

        List<UserStock> userStocks = portfolio.getUserStocks();
        int stockAsset = 0; // 현재 보유하고 있는 주식 자산

        // 가지고 있는 종목들의 가격 합 계산
        for (UserStock userStock : userStocks) {
            String ticker = userStock.getTicker();
            try {
                int stockPrice = getStockPrice(ticker);
                int stockQuantity = userStock.getTotalQuantity();
                stockAsset += (stockPrice * stockQuantity);
            } catch (Exception e) {
                log.warn("주식 가격 조회 실패 (티커: {}, 사용자 ID: {}): {}", ticker, userId, e.getMessage());
            }
        }

        // 총 자산 계산 (현금 + 보유 주식 총액)
        long totalCurrentAsset = portfolio.getBalance() + stockAsset;

        // 수익률 계산
        double returnRate = ((double) (totalCurrentAsset - PRINCIPAL) / PRINCIPAL) * 100;

        // 포트폴리오 정보를 JSON으로 구성
        Map<String, Object> portfolioUpdate = new HashMap<>();
        portfolioUpdate.put("userId", userId);
        portfolioUpdate.put("returnRate", returnRate);
        portfolioUpdate.put("stockAsset", stockAsset);  // 보유 주식 총액
        portfolioUpdate.put("totalAsset", totalCurrentAsset);  // 총 자산 (현금 + 주식)
        portfolioUpdate.put("balance", portfolio.getBalance());  // 현금 잔액

        // 캐시된 데이터와 비교하여 변경된 경우에만 WebSocket 전송
        boolean hasChanged = cachedData == null || 
                !isEqual(cachedData.get("returnRate"), returnRate) ||
                !isEqual(cachedData.get("stockAsset"), stockAsset) ||
                !isEqual(cachedData.get("totalAsset"), totalCurrentAsset) ||
                !isEqual(cachedData.get("balance"), portfolio.getBalance());

        if (hasChanged) {
            try {
                // Redis에 캐시 저장 (TTL: 10초)
                String portfolioJson = objectMapper.writeValueAsString(portfolioUpdate);
                redisTemplate.opsForValue().set(cacheKey, portfolioJson, 10, TimeUnit.SECONDS);
                
                // WebSocket 세션 관리자를 통해 직접 전송
                sessionManager.sendPortfolioUpdate(userId, portfolioUpdate);
                log.debug("포트폴리오 업데이트 전송 - 사용자 ID: {}, 수익률: {}%", userId, returnRate);
            } catch (Exception e) {
                log.error("포트폴리오 WebSocket 전송 또는 캐시 저장 오류 (사용자 ID: {})", userId, e);
            }
        }
    }
    
    /*
    두 값이 동일한지 비교 (소수점 오차 고려)
     */
    private boolean isEqual(Object cachedValue, Object newValue) {
        if (cachedValue == null && newValue == null) return true;
        if (cachedValue == null || newValue == null) return false;
        
        // 숫자 비교 (소수점 오차 허용)
        if (cachedValue instanceof Number && newValue instanceof Number) {
            double cached = ((Number) cachedValue).doubleValue();
            double newVal = ((Number) newValue).doubleValue();
            return Math.abs(cached - newVal) < 0.01; // 0.01% 오차 허용
        }
        
        return cachedValue.equals(newValue);
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
    모든 사용자의 포트폴리오 수익률을 주기적으로 계산하고 실시간 발행 (Redis 캐싱 적용)
    */
    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void updateAllPortfolioReturnRates() {
        long startTime = System.currentTimeMillis();
        int processedCount = 0;
        int updatedCount = 0;
        
        try {
            List<Portfolio> allPortfolios = portfolioRepository.findAll();
            log.debug("포트폴리오 수익률 업데이트 시작 - 총 {}개 포트폴리오", allPortfolios.size());
            
            for (Portfolio portfolio : allPortfolios) {
                try {
                    Long userId = portfolio.getUser().getId();
                    
                    // 포트폴리오 수익률 계산 및 업데이트 (캐시 비교 포함)
                    calculateReturnRate(portfolio);
                    processedCount++;
                    
                    // 보유 주식 정보도 함께 업데이트
                    List<UserStockResponse> userStocks = userStockService.getUserStocksByUserId(userId);
                    
                    // 보유 주식 정보도 Redis 캐싱하여 변경된 경우에만 전송
                    String userStockCacheKey = "userstock:data:" + userId;
                    String cachedUserStockJson = redisTemplate.opsForValue().get(userStockCacheKey);
                    
                    boolean userStockChanged = true;
                    if (cachedUserStockJson != null && !cachedUserStockJson.isBlank()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> cachedUserStock = objectMapper.readValue(cachedUserStockJson, Map.class);
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> cachedStocks = (List<Map<String, Object>>) cachedUserStock.get("userStocks");
                            
                            // 보유 종목 수와 각 종목의 수량 비교
                            if (cachedStocks != null && cachedStocks.size() == userStocks.size()) {
                                boolean allSame = true;
                                for (int i = 0; i < userStocks.size(); i++) {
                                    UserStockResponse current = userStocks.get(i);
                                    Map<String, Object> cached = cachedStocks.get(i);
                                    if (!current.getTicker().equals(cached.get("ticker")) ||
                                        !isEqual(current.getTotalQuantity(), cached.get("totalQuantity")) ||
                                        !isEqual(current.getCurrentPrice(), cached.get("currentPrice"))) {
                                        allSame = false;
                                        break;
                                    }
                                }
                                userStockChanged = !allSame;
                            }
                        } catch (Exception e) {
                            log.warn("캐시된 보유 주식 데이터 비교 실패 (사용자 ID: {}): {}", userId, e.getMessage());
                        }
                    }
                    
                    if (userStockChanged) {
                        Map<String, Object> userStockUpdate = new HashMap<>();
                        userStockUpdate.put("userId", userId);
                        userStockUpdate.put("userStocks", userStocks);
                        
                        // Redis에 캐시 저장 (TTL: 10초)
                        try {
                            String userStockJson = objectMapper.writeValueAsString(userStockUpdate);
                            redisTemplate.opsForValue().set(userStockCacheKey, userStockJson, 10, TimeUnit.SECONDS);
                        } catch (JsonProcessingException e) {
                            log.warn("보유 주식 캐시 저장 실패 (사용자 ID: {}): {}", userId, e.getMessage());
                        }
                        
                        sessionManager.sendUserStockUpdate(userId, userStockUpdate);
                        updatedCount++;
                        log.debug("보유 주식 업데이트 전송 - 사용자 ID: {}, 개수: {}", userId, userStocks.size());
                    }
                    
                } catch (Exception e) {
                    log.warn("포트폴리오 수익률 계산 실패 (사용자 ID: {}): {}", 
                            portfolio.getUser().getId(), e.getMessage());
                }
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("포트폴리오 수익률 업데이트 완료 - 처리: {}, 업데이트: {}, 소요시간: {}ms", 
                    processedCount, updatedCount, elapsedTime);
                    
        } catch (Exception e) {
            log.error("포트폴리오 수익률 업데이트 스케줄러 오류", e);
        }
    }
}
