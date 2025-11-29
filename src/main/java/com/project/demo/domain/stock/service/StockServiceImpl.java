package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.common.kis.KisApiAccessTokenService;
import com.project.demo.common.util.DateUtil;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Primary
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final KisApiAccessTokenService kisApiAccessTokenService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${REAL_BASE_URL}")
    private String baseUrl;

    /*
    한국투자증권의 Access Token 가져오기
     */
    public String getAccessToken() {
        return kisApiAccessTokenService.getAccessToken();
    }

    /*
    전체 주식 정보 반환 (거래량 순)
     */
    @Override
    public List<StockResponse> showAllStock() {
        List<StockResponse> result = new ArrayList<>();

        // 거래량 내림차순 전체 종목
        Set<String> allStocks = redisTemplate.opsForZSet()
                .reverseRange("stock:rank:volume", 0, -1);

        if (allStocks != null) {
            for (String code : allStocks) {
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

    /*
    당일 분봉 수집
     */
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

    /*
    기간별 해당 종목 주가, 거래량 조회(연/월/주/일)
     */
    public List<CandleResponse> getPeriodStockInfo(String ticker, String period) {

        final String endDate = DateUtil.today(); // 오늘 날짜
        final String startDate;
        switch (period) {
            case "D": // 일
                startDate = DateUtil.daysAgo(100); // 90일 전(약 3달치)
                break;
            case "M": // 달
                startDate = DateUtil.monthsAgo(100); // 36달 전(3년치)
                break;
            case "Y": // 연
                startDate = DateUtil.yearsAgo(100); // 20년 전(20년치)
                break;
            case "W": // 주
                startDate = DateUtil.weeksAgo(100); // 24주 전(약 6개월 치)
                break;
            default:
                startDate = DateUtil.daysAgo(100);
                break;
        }

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

    /*

     */
    @Override
    public List<CandleResponse> getPeriodStockInfoByRange(String ticker, String period, String startDate, String endDate) {
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

    /*
    Redis에서 실시간 체결가 가져오기
     */
    @Override
    public int getCurrentPrice(String ticker) {
        try {
            String key = "stock:data:" + ticker;
            String json = redisTemplate.opsForValue().get(key);
            
            if (json == null) {
                log.warn("Redis에서 주가 데이터를 찾을 수 없음 - 티커: {}", ticker);
                return 0;
            }
            
            JsonNode data = objectMapper.readTree(json);
            int price = data.get("price").asInt();
            
            log.debug("현재 주가 조회 - 티커: {}, 가격: {}", ticker, price);
            return price;
            
        } catch (Exception e) {
            log.error("현재 주가 조회 실패 - 티커: {}, 오류: {}", ticker, e.getMessage());
            return 0;
        }
    }
 
    /*
    기업 개요 조회
     */
    @Override
    public String getStockOutline(String ticker) {
        return stockRepository.findByTicker(ticker)
                .map(stock -> {
                    String outline = stock.getOutline();
                    log.info("기업 개요 조회 - ticker: {}, outline 존재: {}, outline 길이: {}", 
                            ticker, outline != null, outline != null ? outline.length() : 0);
                    return outline;
                })
                .orElse(null);
    }
}
