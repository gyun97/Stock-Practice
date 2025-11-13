package com.project.demo.domain.userstock.service;

import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.stock.service.StockService;
import com.project.demo.domain.userstock.dto.response.UserStockResponse;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStockServiceImpl implements UserStockService {

    private final UserStockRepository userStockRepository;
    private final StockService stockService;
    private final StockRepository stockRepository;

    @Override
    public List<UserStockResponse> getUserStocksByUserId(Long userId) {
        log.info("사용자 보유 주식 조회 - 사용자 ID: {}", userId);
        
        List<UserStock> userStocks = userStockRepository.findByUserId(userId);

        log.info("조회된 보유 주식 수: {}", userStocks.size());

        return userStocks.stream()
                .map(userStock -> {
                    try {
                        // 현재 주가 조회
                        int currentPrice = stockService.getCurrentPrice(userStock.getTicker());
                        
                        // 회사명 조회 (ticker를 사용하여)
                        String companyName = getCompanyNameByTicker(userStock.getTicker());
                        
                        return UserStockResponse.of(
                                userStock.getTicker(),
                                companyName,
                                userStock.getTotalQuantity(),
                                userStock.getAvgPrice(),
                                currentPrice
                        );
                    } catch (Exception e) {
                        log.error("보유 주식 정보 변환 실패 - 티커: {}, 오류: {}", 
                                userStock.getTicker(), e.getMessage());
                        return null;
                    }
                })
                .filter(response -> response != null)
                .collect(Collectors.toList());
    }
    
    /**
     * 티커로 회사명 조회
     */
    private String getCompanyNameByTicker(String ticker) {
        try {
            String companyName = stockRepository.findNameByTicker(ticker);
            log.debug("티커 {}의 회사명: {}", ticker, companyName);
            return companyName != null ? companyName : ticker; // 회사명이 없으면 티커 반환
        } catch (Exception e) {
            log.error("회사명 조회 실패 - 티커: {}, 오류: {}", ticker, e.getMessage());
            return ticker; // 오류 시 티커 반환
        }
    }
}