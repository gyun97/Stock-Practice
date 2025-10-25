package com.project.demo.domain.stock.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockService stockService;

    /**
     * 전체 주식 정보 가져오기
     *
     * @return 각 주식의 [종목 코드, 가격, 주가 변화량, 등락률, 회사 이름, 체결 시간, 누적 거래량] 리스트
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockResponse>>> showAll() {
        return ResponseEntity.ok(ApiResponse.requestSuccess(stockService.showAllStock()));
    }


    @GetMapping("/{ticker}/period")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getPeriodStockInfo(
            @PathVariable String ticker, @RequestParam(required = true) String period) {

        List<CandleResponse> response = stockService.getPeriodStockInfo(ticker, period);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response));
    }

}
