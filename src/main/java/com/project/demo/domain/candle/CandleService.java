package com.project.demo.domain.candle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CandleService {

    private final WebClient webClient;
    private final CandleRepository candleRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    private String accessToken;

    // KIS API 호출 메서드
    public void fetchAndSaveCandles(String ticker) {
        accessToken = redisTemplate.opsForValue().get("kis:access_token");
        Map response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", ticker) // 종목코드
                        .queryParam("FID_INPUT_HOUR_1", "090000") // 9시부터 조회 (스케줄러에서 현재시간 넣어도 됨)
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .queryParam("FID_ETC_CLS_CODE", "")
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST03010200")
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("output2") == null) return;

        List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.get("output2");

        for (Map<String, Object> o : outputs) {
            String date = (String) o.get("stck_bsop_date");
            String time = (String) o.get("stck_cntg_hour");

            if (candleRepository.existsByTickerAndDateAndTime(ticker, date, time)) continue;

            Candle candle = Candle.builder()
                    .ticker(ticker)
                    .date(date)
                    .time(time)
                    .open(Long.parseLong((String) o.get("stck_oprc")))
                    .high(Long.parseLong((String) o.get("stck_hgpr")))
                    .low(Long.parseLong((String) o.get("stck_lwpr")))
                    .close(Long.parseLong((String) o.get("stck_prpr")))
                    .volume(Long.parseLong((String) o.get("cntg_vol")))
                    .build();

            candleRepository.save(candle);
        }

        log.info("캔들 데이터 저장 완료: {}", ticker);
    }
}

