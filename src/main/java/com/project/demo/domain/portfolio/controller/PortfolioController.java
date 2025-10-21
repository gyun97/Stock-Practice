package com.project.demo.domain.portfolio.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;

    /**
     * 내 포토폴리오 조회 API
     * @param userId
     * @return 현금 잔액(가용 자산), 보유 주식 총액, 총 자산, 보유 종목 수, 보유 주식 수량, 현재 수익률
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getMyPortfolio(@PathVariable Long userId) {
        PortfolioResponse response = portfolioService.getMyPortfolio(userId);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response));
    }
}
