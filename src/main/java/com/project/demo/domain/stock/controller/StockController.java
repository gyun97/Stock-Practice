package com.project.demo.domain.stock.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.KospiDataPoint;
import com.project.demo.domain.stock.dto.response.KospiResponse;
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

    @GetMapping("/{ticker}/period/range")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getPeriodStockInfoByRange(
            @PathVariable String ticker,
            @RequestParam(required = true) String period,
            @RequestParam(required = true) String startDate,
            @RequestParam(required = true) String endDate) {

        List<CandleResponse> response = stockService.getPeriodStockInfoByRange(ticker, period, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response));
    }

    /**
     * 특정 종목의 기업 개요 조회
     */
    @GetMapping("/{ticker}/outline")
    public ResponseEntity<ApiResponse<String>> getStockOutline(@PathVariable String ticker) {
        log.info("기업 개요 API 호출 - ticker: {}", ticker);
        String outline = stockService.getStockOutline(ticker);
        log.info("기업 개요 API 응답 - ticker: {}, outline: {}", ticker,
                outline != null ? outline.substring(0, Math.min(50, outline.length())) : "null");
        return ResponseEntity.ok(ApiResponse.requestSuccess(outline));
    }

    /**
     * 코스피 현재 지수 + 통계 조회
     */
    @GetMapping("/kospi")
    public ResponseEntity<ApiResponse<KospiResponse>> getKospiIndex() {
        // log.info("코스피 현재 지수 API 호출");
        KospiResponse response = stockService.getKospiIndex();
        return ResponseEntity.ok(ApiResponse.requestSuccess(response));
    }

    /**
     * 코스피 기간별 차트 데이터 조회
     * 
     * @param period D(일) / W(주) / M(월) / Y(연)
     */
    @GetMapping("/kospi/history")
    public ResponseEntity<ApiResponse<List<KospiDataPoint>>> getKospiHistory(
            @RequestParam(defaultValue = "D") String period) {
        // log.info("코스피 히스토리 API 호출 - period: {}", period);
        List<KospiDataPoint> response = stockService.getKospiHistory(period);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response));
    }

}
