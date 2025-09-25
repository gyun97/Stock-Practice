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

    @PostMapping("/{trId}/{trKey}")
    public ResponseEntity<ApiResponse<String>> getStockPrice(@PathVariable String trId, @PathVariable String trKey) throws Exception {
        stockService.getTradedPrice(trId, trKey); // 종목 구독 체결
        return ResponseEntity.ok(ApiResponse.createSuccess(trKey + ": 구독 성공"));
    }
}
