package com.project.demo.domain.stock.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.stock.service.StockServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {

    private final StockServiceImpl stockService;

    /**
     * 구독 종목 주식 정보 조회
     * @param trId
     * @param trKey
     * @return
     * @throws Exception
     */
    @GetMapping("/{trId}/{trKey}")
    public ResponseEntity<ApiResponse<String>> getStockPrice(@PathVariable String trId, @PathVariable String trKey) throws Exception {
        stockService.getStockInfo(trId, trKey); // 종목 구독 체결
        return ResponseEntity.ok(ApiResponse.createSuccess(trKey + ": 정보 조회 성공"));
    }
}
