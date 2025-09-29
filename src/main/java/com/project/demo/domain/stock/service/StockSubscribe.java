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
import java.util.Optional;
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

    // 서버 가동시 시가 총액 상위 30위 주식들 정보 가져와서 RDB 저장
    @Transactional
    @EventListener(ApplicationReadyEvent.class) // 스프링 애플리케이션이 완전히 시작된 후에 특정 메서드를 실행하는 어노테이션
    @Order(1)
    public void saveTop30Stocks() {
        approvalKey = approvalKeyService.getApprovalKey(); // KIS WebSocket 연결에 필요한 승인 키
        accessToken = kisApiAccessTokenService.getAccessToken(); // KIS REST API 호출에 필요한 Access Token 발급 키
        Map response = webClient.get()// SpringFlux의 WebClient로 KIS API에 요청 생성 및 전송
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/ranking/market-cap") // url
                        .queryParam("fid_input_price_2", "0") // 입력 가격2(입력값 없을때 전체 (~ 가격))
                        .queryParam("fid_cond_mrkt_div_code", "J") // 시장구분코드 (J:KRX, NX:NXT)
                        .queryParam("fid_cond_scr_div_code", "20174") // Unique key( 20174 )
                        .queryParam("fid_div_cls_code", "0") // 분류 구분 코드(0: 전체, 1:보통주, 2:우선주)
                        .queryParam("fid_input_iscd", "0001") // 입력 종목코드(0000:전체, 0001:거래소, 1001:코스닥, 2001:코스피200)
                        .queryParam("fid_trgt_cls_code", "0") // 대상 구분 코드(0 : 전체)
                        .queryParam("fid_trgt_exls_cls_code", "0") // 대상 제외 구분 코드(0 : 전체)
                        .queryParam("fid_input_price_1", "0") // 입력 가격1(입력값 없을때 전체 (가격 ~))
                        .queryParam("fid_vol_cnt", "0") // 거래량 수(입력값 없을때 전체 (거래량 ~))
                        .build())
                .header("content-type", "application/json; charset=utf-8")
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHPST01740000") // 거래ID
                .header("custtype", "P") // 개인
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || response.get("output") == null) return;

        List<Map<String, Object>> outputs = (List<Map<String, Object>>) response.get("output");

        for (Map<String, Object> stockData : outputs) {
            String ticker = (String) stockData.get("mksc_shrn_iscd"); // 주식 종목 구분 코드
            String name = (String) stockData.get("hts_kor_isnm"); // 주식 종목 이름
            Long volume = Long.parseLong((String) stockData.get("acml_vol")); // 누적 거래량
            Market market = Market.KOSPI; // 시장 종류

            // 해당 종목 정보 RDB 저장
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

        // 만약 장시간이라면 30개 종목들 구독해서 KIS WebSocket으로 실시간 체결가 가져오기
        if (MarketTime.isMarketOpen()) {
            for (String ticker : allTickers) {
                subscribeStock(ticker); // 해당 종목 구독
            }
        } else { // 만약 장외시간이라면
            for (String ticker : allTickers) {
                getStockPriceRest(ticker); // Rest API로 종가 가져오기
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
        client.send(json); // KIS 웹소켓에 해당 종목 구독 요청 전송하면 KIS 웹소켓이 해당 종목 실시간 데이터 보내줌
    }

    // 해당 주식 종목 구독 해제
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

    public void getStockPriceRest(String trKey) {
        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price";

        log.info("url: {}", url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100");

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("fid_cond_mrkt_div_code", "J")
                .queryParam("fid_input_iscd", trKey);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, Map.class
        );

        try {
            Map body = response.getBody();
            if (body == null) return;

            Object output = body.get("output");
            Map<String, Object> om = (Map<String, Object>) output;

            double price = Double.parseDouble(Optional.ofNullable(om.get("stck_prpr")).orElse("0").toString());
            double changeAmount = Double.parseDouble(Optional.ofNullable(om.get("prdy_vrss")).orElse("0").toString());
            double changeRate = Double.parseDouble(Optional.ofNullable(om.get("prdy_ctrt")).orElse("0").toString());

            // Redis에 기존 데이터가 있으면 tradeTime 유지
            String key = "stock:data:" + trKey;
            String existingJson = redisTemplate.opsForValue().get(key);

            String tradeTime = null;
            if (existingJson != null) {
                JsonNode existingNode = mapper.readTree(existingJson);
                if (existingNode.has("tradeTime")) {
                    tradeTime = existingNode.get("tradeTime").asText();
                }
            }

            String companyName = stockRepository.findNameByTicker(trKey); // 회사 이름

            ObjectNode out = mapper.createObjectNode();
            out.put("stockCode", trKey);
            out.put("price", price);
            out.put("changeAmount", changeAmount);
            out.put("changeRate", changeRate);
            out.put("companyName", companyName);
            if (tradeTime != null) {
                out.put("tradeTime", tradeTime); // WebSocket에서 갱신된 값 유지
            }

            String json = mapper.writeValueAsString(out);

            redisTemplate.opsForValue().set(key, json);
            redisTemplate.convertAndSend("stock:updates", json);

            log.info("Redis 저장 & Pub/Sub 발행(REST) → {}", json);

        } catch (Exception e) {
            log.warn("REST 현재가 브로드캐스트 실패", e);
        }
    }
}
