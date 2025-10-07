package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.util.DateUtil;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Primary
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${REAL_BASE_URL}")
    private String baseUrl;

    public String getAccessToken() {
        return redisTemplate.opsForValue().get("kis:access_token");
    }

    // 전체 주식 30개 거래량 순으로 정보 반환
    @Override
    public List<StockResponse> showAllStock() {
        List<StockResponse> result = new ArrayList<>();

        // 거래량 내림차순 Top30
        Set<String> volumeTop30 = redisTemplate.opsForZSet()
                .reverseRange("stock:rank:volume", 0, 29);

        if (volumeTop30 != null) {
            for (String code : volumeTop30) {
                String json = redisTemplate.opsForValue().get("stock:data:" + code);
                if (json != null && !json.isBlank()) {
                    try {
                        StockResponse stock = objectMapper.readValue(json, StockResponse.class);
                        result.add(stock);
                    } catch (Exception e) {
                        log.error("JSON 파싱 실패 key={}", code, e);
                    }
                }
            }
        }

        return result;
    }

    // 당일 분봉 수집
    public List<StockResponse> getMinuteCandles(String ticker, String date, String time) {
        String url = "uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // KRX
                        .queryParam("FID_INPUT_ISCD", ticker)       // ex: 005930
                        .queryParam("FID_INPUT_HOUR_1", time)       // ex: 090000
                        .queryParam("FID_INPUT_DATE_1", date)       // ex: 20241023
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .queryParam("FID_FAKE_TICK_INCU_YN", "N")
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .header("authorization", "Bearer " + getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST03010230")
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    List<StockResponse> stocks = new ArrayList<>();
                    if (json.has("output2")) {
                        for (JsonNode node : json.get("output2")) {
                            StockResponse candle = StockResponse.builder()
                                    .tradeTime(node.get("stck_cntg_hour").asText())
                                    .price(node.get("stck_prpr").asInt())
                                    .build();
                            stocks.add(candle);
                        }
                    }
                    return stocks;
                })
                .block();
    }

    // 기간별 해당 종목 주가, 거래량 조회(연/월/주/일)
    public List<CandleResponse> getPeriodStockInfo(String ticker, String period) {
        String endDate = DateUtil.today(); // 오늘 날짜
        String tmpStartDate = "";
        switch (period) {
            case "D": // 일
                tmpStartDate = DateUtil.daysAgo(30); // 30일 전(약 한달치)
                break;
            case "M": // 달
                tmpStartDate = DateUtil.monthsAgo(12); // 12달 전(1년치)
                break;
            case "Y": // 연
                tmpStartDate = DateUtil.yearsAgo(10); // 10년 전(10년치)
                break;
            case "W": // 주
                tmpStartDate = DateUtil.weeksAgo(24); // 24주 전(약 6개 치)
                break;
        }
        String startDate = tmpStartDate;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", ticker)
                        .queryParam("FID_INPUT_DATE_1", startDate)
                        .queryParam("FID_INPUT_DATE_2", endDate)
                        .queryParam("FID_PERIOD_DIV_CODE", period) // D/W/M/Y
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("authorization", "Bearer " + getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST03010100")
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    List<CandleResponse> candles = new ArrayList<>();
                    if (json.has("output2")) {
                        for (JsonNode node : json.get("output2")) {
                            CandleResponse candle = CandleResponse.builder()
                                    .date(node.get("stck_bsop_date").asText())
                                    .open(node.get("stck_oprc").asInt())
                                    .high(node.get("stck_hgpr").asInt())
                                    .low(node.get("stck_lwpr").asInt())
                                    .close(node.get("stck_clpr").asInt())
                                    .volume(node.get("acml_vol").asLong())
                                    .build();
                            candles.add(candle);
                        }
                    }
                    return candles;
                })
                .block();
    }
}
