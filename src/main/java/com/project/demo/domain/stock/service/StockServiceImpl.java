package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.kis.KisApiAccessTokenService;
import com.project.demo.common.util.DateUtil;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.KospiDataPoint;
import com.project.demo.domain.stock.dto.response.KospiResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.stock.util.YahooFinanceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
    private final KisApiAccessTokenService kisApiAccessTokenService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final StockMetrics stockMetrics;
    private final YahooFinanceUtil yahooFinanceUtil;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${kis.url.rest}")
    private String baseUrl;

    /*
     * 한국투자증권의 Access Token 가져오기
     */
    public String getAccessToken() {
        return kisApiAccessTokenService.getAccessToken();
    }

    /*
     * 전체 주식 정보 반환 (거래량 순)
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
     * 당일 분봉 수집
     */
    public List<StockResponse> getMinuteCandles(String ticker, String date, String time) {
        String url = "uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(url)
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J") // KRX
                        .queryParam("FID_INPUT_ISCD", ticker) // ex: 005930
                        .queryParam("FID_INPUT_HOUR_1", time) // ex: 090000
                        .queryParam("FID_INPUT_DATE_1", date) // ex: 20241023
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
                .onStatus(status -> status.isError(), response -> {
                    stockMetrics.recordKisApiError();
                    return response.createException();
                })
                .bodyToMono(JsonNode.class)
                .doOnNext(json -> stockMetrics.recordKisApiCall())
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
     * 기간별 해당 종목 주가, 거래량 조회(연/월/주/일)
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
                .onStatus(status -> status.isError(), response -> {
                    stockMetrics.recordKisApiError();
                    return response.createException();
                })
                .bodyToMono(JsonNode.class)
                .doOnNext(json -> stockMetrics.recordKisApiCall())
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

    @Override
    public List<CandleResponse> getPeriodStockInfoByRange(String ticker, String period, String startDate,
            String endDate) {
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
                .onStatus(status -> status.isError(), response -> {
                    stockMetrics.recordKisApiError();
                    return response.createException();
                })
                .bodyToMono(JsonNode.class)
                .doOnNext(json -> stockMetrics.recordKisApiCall())
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

    @Override
    public KospiResponse getKospiIndex() {
        final String CACHE_KEY = "kospi:current";

        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, KospiResponse.class);
            } catch (Exception e) {
                log.warn("코스피 캐시 역직렬화 실패, 재조회", e);
            }
        }

        String today = DateUtil.today();
        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-index-price?FID_COND_MRKT_DIV_CODE=U&FID_INPUT_ISCD=0001";

        KospiResponse result = webClient.get()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .header("authorization", "Bearer " + getAccessToken())
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKUP01010100")
                .header("custtype", "P")
                .retrieve()
                .onStatus(status -> status.isError(), response -> {
                    return response.bodyToMono(JsonNode.class).flatMap(json -> {
                        String msgCd = json.path("msg_cd").asText();
                        String msg1 = json.path("msg1").asText();
                        if ("EGW00201".equals(msgCd)) {
                            return Mono.error(new RuntimeException("RATE_LIMIT_EXCEEDED"));
                        }
                        stockMetrics.recordKisApiError();
                        return Mono.error(new RuntimeException("KIS API Error: " + msg1));
                    });
                })
                .bodyToMono(JsonNode.class)
                .retryWhen(reactor.util.retry.Retry.fixedDelay(2, java.time.Duration.ofSeconds(1))
                        .filter(throwable -> "RATE_LIMIT_EXCEEDED".equals(throwable.getMessage())))
                .timeout(java.time.Duration.ofSeconds(5))
                .map(json -> {
                    JsonNode output = json.path("output");
                    if (output.isMissingNode() || output.isNull()) {
                        return null;
                    }

                    double currentIndex = output.path("bstp_nmix_prpr").asDouble();
                    if (currentIndex == 0) return null;

                    return KospiResponse.builder()
                            .currentIndex(currentIndex)
                            .change(output.path("bstp_nmix_prdy_vrss").asDouble())
                            .changeRate(output.path("bstp_nmix_prdy_ctrt").asDouble())
                            .open(output.path("bstp_nmix_oprc").asDouble())
                            .high(output.path("bstp_nmix_hgpr").asDouble())
                            .low(output.path("bstp_nmix_lwpr").asDouble())
                            .prevClose(output.path("bstp_nmix_prdy_clpr").asDouble())
                            .high52w(output.path("d250_hgpr").asDouble())
                            .low52w(output.path("d250_lwpr").asDouble())
                            .baseDate(output.path("stck_bsop_date").asText(today))
                            .build();
                })
                .onErrorResume(e -> yahooFinanceUtil.fetchCurrentKospi())
                .block();

        if (result != null) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(result), Duration.ofMinutes(2));
            } catch (Exception e) {}
        }

        if (result == null) {
            return KospiResponse.builder().currentIndex(0.0).baseDate(today).build();
        }
        return result;
    }

    @Override
    public List<KospiDataPoint> getKospiHistory(String period) {
        final String CACHE_KEY = "kospi:history:" + period;

        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, KospiDataPoint.class));
            } catch (Exception e) {}
        }

        String range = "1d";
        String interval = "2m";
        
        switch (period) {
            case "W": range = "5d"; interval = "15m"; break;
            case "M": range = "1mo"; interval = "1d"; break;
            case "Y": range = "1y"; interval = "1d"; break;
            default: range = "1d"; interval = "2m"; break;
        }

        List<KospiDataPoint> result = yahooFinanceUtil.fetchKospiHistory(range, interval)
                .onErrorResume(e -> Mono.just(new ArrayList<>()))
                .block();

        if (result != null && !result.isEmpty()) {
            result.sort(java.util.Comparator.comparing(KospiDataPoint::getDate));
            // 1D는 2분, 나머지는 10분 캐시
            long ttlMinutes = "D".equals(period) ? 2 : 10;
            try {
                redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(result), Duration.ofMinutes(ttlMinutes));
            } catch (Exception e) {}
            return result;
        }

        return new ArrayList<>();
    }
}
