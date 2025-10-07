package com.project.demo.domain.stock.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
        return ResponseEntity.ok(ApiResponse.createSuccess(stockService.showAllStock()));
    }

//    @GetMapping("/{ticker}")
//    public ResponseEntity<ApiResponse<List<StockResponse>>> getMinuteCandles(
//            @PathVariable String ticker,
//            @RequestParam(required = false) String date,
//            @RequestParam(required = false) String time) {
//
//        // 기본값: 현재 일자
//        if (date == null) date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//
//        // 기본값: 현재 시각 (HHmmss)
//        if (time == null) time = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
//
//        List<StockResponse> minuteCandles = stockService.getMinuteCandles(ticker, date, time);
//        return ResponseEntity.ok(ApiResponse.createSuccess(minuteCandles));
//    }

    @GetMapping("/{ticker}/period")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getPeriodStockInfo(
            @PathVariable String ticker, @RequestParam(required = true) String period) {

        List<CandleResponse> response = stockService.getPeriodStockInfo(ticker, period);
        return ResponseEntity.ok(ApiResponse.createSuccess(response));
    }

}
