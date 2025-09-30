package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

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
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    private String accessToken;

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

    public List<CandleResponse> getMinuteCandles(String ticker, String date, String time) {
        String url = "https://openapi.koreainvestment.com:9443/uapi/domestic-stock/v1/quotations/inquire-time-dailychartprice";

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(url.replace("https://openapi.koreainvestment.com:9443", "")) // base 제거
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // KRX
                        .queryParam("FID_INPUT_ISCD", ticker)       // ex: 005930
                        .queryParam("FID_INPUT_HOUR_1", time)       // ex: 090000
                        .queryParam("FID_INPUT_DATE_1", date)       // ex: 20241023
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .queryParam("FID_FAKE_TICK_INCU_YN", "N")
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .header("authorization", "Bearer " + redisTemplate.opsForValue().get("kis:access_token"))
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST03010230")
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    List<CandleResponse> candles = new ArrayList<>();
                    if (json.has("output2")) {
                        for (JsonNode node : json.get("output2")) {
                            CandleResponse candle = CandleResponse.builder()
                                    .date(node.get("stck_bsop_date").asText())
                                    .time(node.get("stck_cntg_hour").asText())
                                    .open(node.get("stck_oprc").asInt())
                                    .high(node.get("stck_hgpr").asInt())
                                    .low(node.get("stck_lwpr").asInt())
                                    .close(node.get("stck_prpr").asInt())
                                    .volume(node.get("cntg_vol").asLong())
                                    .build();
                            candles.add(candle);
                        }
                    }
                    return candles;
                })
                .block();
    }



}
