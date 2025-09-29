package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.oauth.service.KisApiAccessTokenService;
import com.project.demo.common.oauth.service.KisApprovalKeyService;
import com.project.demo.common.time.MarketTime;
import com.project.demo.common.websocket.ConnectWebSocketClient;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.enums.Market;
import com.project.demo.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockSubscribe {

    private final ObjectMapper mapper;
    private final KisApprovalKeyService approvalKeyService;
    private final KisApiAccessTokenService kisApiAccessTokenService;
    private final StockBroadcastService stockBroadcastService;
    private final StockRepository stockRepository;
    private final WebClient webClient;
    private final ConnectWebSocketClient client;
    private final StringRedisTemplate redisTemplate;

    @Value("${KIS_APP_KEY}")
    private String appKey;

    @Value("${KIS_APP_SECRET}")
    private String appSecret;

    @Value("${REAL_BASE_URL}")
    private String baseUrl;

    private String iv;
    private String key;

    private String approvalKey;
    private String accessToken;

    // 서버 가동시 시가 총액 상위 30위 주식들 정보 가져오기
    @Transactional
    @EventListener(ApplicationReadyEvent.class) // 스프링 애플리케이션이 완전히 시작된 후에 특정 메서드를 실행하는 어노테이션
    @Order(1)
    public void saveTop30Stocks() {
        approvalKey = approvalKeyService.getApprovalKey(); // 웹소켓 인증 키 발급
        accessToken = kisApiAccessTokenService.getAccessToken();
        Map response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/ranking/market-cap")
                        .queryParam("fid_input_price_2", "0")
                        .queryParam("fid_cond_mrkt_div_code", "J")
                        .queryParam("fid_cond_scr_div_code", "20174")
                        .queryParam("fid_div_cls_code", "0")
                        .queryParam("fid_input_iscd", "0001")
                        .queryParam("fid_trgt_cls_code", "0")
                        .queryParam("fid_trgt_exls_cls_code", "0")
                        .queryParam("fid_input_price_1", "0")
                        .queryParam("fid_vol_cnt", "0")
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHPST01740000")
                .header("custtype", "P")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("output") == null) return;

        List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.get("output");

        for (Map<String, Object> stockData : outputs) {
            String ticker = (String) stockData.get("mksc_shrn_iscd");
            String name = (String) stockData.get("hts_kor_isnm");
            Long volume = Long.parseLong((String) stockData.get("acml_vol"));
            Market market = Market.KOSPI;

            if (!stockRepository.existsByTicker(ticker)) {
                Stock stock = Stock.builder()
                        .ticker(ticker)
                        .name(name)
                        .market(market)
                        .volume(volume)
                        .build();
                stockRepository.save(stock);
            }
        }
    }

    // 메인 화면에 표기될 전체 주식들 정보 얻기 위해 구독
//    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void subscribeTop30(ApplicationReadyEvent event) throws JsonProcessingException, InterruptedException {
        Set<String> allTickers = stockRepository.findAllTickers();

        if (MarketTime.isMarketOpen()) {
            for (String ticker : allTickers) {
                subscribeStock(ticker);
            }
        } else {
            for (String ticker : allTickers) {
                getStockPriceRest(ticker);
                Thread.sleep(500); // 0.1초 딜레이 → 초당 10건
            }
        }
    }

    // 해당 주식 종목 구독
    private void subscribeStock(String ticker) throws JsonProcessingException {
        ObjectNode header = mapper.createObjectNode();

        header.put("approval_key", approvalKey);
        header.put("custtype", "P"); // 개인: P / 법인 : B
        header.put("tr_type", "1"); // 구독 신청
        header.put("content-type", "utf-8");

        ObjectNode input = mapper.createObjectNode();
        input.put("tr_id", "H0STCNT0"); // TR ID
        input.put("tr_key", ticker); // 해당 주식의 종목 코드

        ObjectNode body = mapper.createObjectNode();
        body.set("input", input);

        ObjectNode request = mapper.createObjectNode();
        request.set("header", header);
        request.set("body", body);

        String json = mapper.writeValueAsString(request);
        client.send(json);
    }

    // 해당 주식 종목 구독
    private void unsubscribeStock(String ticker) throws JsonProcessingException {
        ObjectNode header = mapper.createObjectNode();

        header.put("approval_key", approvalKey);
        header.put("custtype", "P");
        header.put("tr_type", "2"); // 구독 해제
        header.put("content-type", "utf-8");

        ObjectNode input = mapper.createObjectNode();
        input.put("tr_id", "H0STCNT0");
        input.put("tr_key", ticker);

        ObjectNode body = mapper.createObjectNode();
        body.set("input", input);

        ObjectNode request = mapper.createObjectNode();
        request.set("header", header);
        request.set("body", body);

        String json = mapper.writeValueAsString(request);
        client.send(json);
    }


    // 캐시 적용
//    @Cacheable(value = "stock:price", key = "#stockId")
    public void getStockPriceRest(String trKey) {
        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price";

        log.info("url: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken); // Access Token 필요
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100"); // 국내 주식 현재가 조회 TR_ID

        // 쿼리 파라미터
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("fid_cond_mrkt_div_code", "J") // J: 주식
                .queryParam("fid_input_iscd", trKey);      // 종목 코드

        HttpEntity<String> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                Map.class
        );

        log.info("주식 현재가 조회 응답: {}", response.getBody());

        try {
            Double price = null;
            Map body = response.getBody();
            if (body != null) {
                Object output = body.get("output");
                if (output instanceof Map<?, ?> om) {
                    Object p = om.get("stck_prpr"); // KIS 현재가 필드
                    if (p != null) {
                        try {
                            price = Double.valueOf(p.toString());
                        } catch (Exception ignore) {
                        }
                    }
                }
            }

            String tradeTime = LocalTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("HHmmss"));
            ObjectNode out = mapper.createObjectNode();
            out.put("stockCode", trKey);
            if (price != null) out.put("price", price);
            out.put("tradeTime", tradeTime);

            stockBroadcastService.broadcast(out.toString());
            redisTemplate.opsForValue().set("stock:price:" + trKey, String.valueOf(price)); // Redis에 종목 코드와 실시간 현재가 저장
        } catch (Exception e) {
            log.warn("REST 현재가 브로드캐스트 실패", e);
        }
    }


    // 웹소켓 데이터 수신 → Redis 저장 (ConnectWebSocketClient에서 호출)
    public void handleWebSocketMessage(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            String ticker = node.path("tr_key").asText();   // 종목 코드
            double price = node.path("stck_prpr").asDouble(); // 현재가

            // Redis에 저장
            redisTemplate.opsForValue().set("stock:" + ticker, String.valueOf(price));

            log.info("Redis 저장 완료 → {} : {}", ticker, price);

            // 필요하면 브로드캐스트
            stockBroadcastService.broadcast(message);
        } catch (Exception e) {
            log.error("웹소켓 메시지 처리 실패", e);
        }
    }


}
