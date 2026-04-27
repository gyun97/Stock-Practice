package com.project.demo.domain.stock.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.domain.stock.dto.response.KospiDataPoint;
import com.project.demo.domain.stock.dto.response.KospiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class YahooFinanceUtil {

    private static final String BASE = "https://query1.finance.yahoo.com/v8/finance/chart/%5EKS11";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from Yahoo Finance");
        }
        return resp.body();
    }

    public Mono<KospiResponse> fetchCurrentKospi() {
        return Mono.fromCallable(() -> {
            String url = BASE + "?interval=2m&range=1d";
            log.debug("Yahoo Finance 현재가 요청: {}", url);
            String body = get(url);
            return parseCurrentKospi(body);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("Yahoo Finance 현재가 조회 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return Mono.empty();
        });
    }

    public Mono<List<KospiDataPoint>> fetchKospiHistory(String range, String interval) {
        return Mono.fromCallable(() -> {
            String url = BASE + "?range=" + range + "&interval=" + interval;
            log.debug("Yahoo Finance 히스토리 요청: {}", url);
            String body = get(url);
            List<KospiDataPoint> list = parseKospiHistory(body, range);
            if (list.isEmpty() && "1d".equals(range)) {
                log.warn("KOSPI 1d 데이터가 비어있어 5d로 재시도합니다.");
                url = BASE + "?range=5d&interval=" + interval;
                body = get(url);
                list = parseKospiHistory(body, "5d");
            }
            return list;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorResume(e -> {
            log.error("Yahoo Finance 히스토리 조회 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return Mono.just(new ArrayList<>());
        });
    }

    private KospiResponse parseCurrentKospi(String jsonStr) {
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode meta = root.path("chart").path("result").path(0).path("meta");
            double price = meta.path("regularMarketPrice").asDouble();
            double prevClose = meta.path("chartPreviousClose").asDouble();
            double change = price - prevClose;
            double changeRate = prevClose != 0 ? (change / prevClose) * 100 : 0;

            log.info("Yahoo Finance 현재가 파싱 완료: KOSPI={}", price);

            return KospiResponse.builder()
                    .currentIndex(price)
                    .change(change)
                    .changeRate(changeRate)
                    .open(meta.path("regularMarketOpen").asDouble(price))
                    .high(meta.path("regularMarketDayHigh").asDouble(price))
                    .low(meta.path("regularMarketDayLow").asDouble(price))
                    .prevClose(prevClose)
                    .high52w(meta.path("fiftyTwoWeekHigh").asDouble(0))
                    .low52w(meta.path("fiftyTwoWeekLow").asDouble(0))
                    .baseDate(DateTimeFormatter.ofPattern("yyyyMMdd")
                            .withZone(ZoneId.of("Asia/Seoul"))
                            .format(Instant.now()))
                    .build();
        } catch (Exception e) {
            log.error("Yahoo Finance 현재가 파싱 실패", e);
            return null;
        }
    }

    private List<KospiDataPoint> parseKospiHistory(String jsonStr, String range) {
        List<KospiDataPoint> points = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode result = root.path("chart").path("result").path(0);
            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").path(0).path("close");

            if (timestamps.isMissingNode() || timestamps.isEmpty()) {
                log.warn("Yahoo Finance 데이터에 timestamp 필드가 없거나 비어있습니다. range={}", range);
                return points;
            }

            boolean isIntraday = !range.endsWith("y") && !range.endsWith("mo") && "1d".equals(range);
            DateTimeFormatter formatter = isIntraday
                    ? DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul"))
                    : DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Asia/Seoul"));

            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode closeNode = closes.path(i);
                if (closeNode.isNull() || closeNode.isMissingNode()) continue;
                double val = closeNode.asDouble();
                if (val == 0) continue;
                String dateStr = formatter.format(Instant.ofEpochSecond(timestamps.path(i).asLong()));
                points.add(new KospiDataPoint(dateStr, val));
            }

            // 중복 날짜 제거: 같은 날짜면 마지막 값만 유지 (순서 보장을 위해 LinkedHashMap 사용)
            java.util.LinkedHashMap<String, KospiDataPoint> deduped = new java.util.LinkedHashMap<>();
            for (KospiDataPoint p : points) {
                deduped.put(p.getDate(), p);
            }
            points = new ArrayList<>(deduped.values());
            log.info("Yahoo Finance 히스토리 파싱 완료: {}개 포인트 (range={})", points.size(), range);
        } catch (Exception e) {
            log.error("Yahoo Finance 히스토리 파싱 실패", e);
        }
        return points;
    }
}
