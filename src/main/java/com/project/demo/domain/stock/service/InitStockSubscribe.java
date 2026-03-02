package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.kis.KisApiAccessTokenService;
import com.project.demo.common.kis.KisApprovalKeyService;
import com.project.demo.common.util.MarketTime;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!test") // test 프로파일에서는 제외
public class InitStockSubscribe {

    private final ObjectMapper mapper;
    private final KisApprovalKeyService approvalKeyService;
    private final KisApiAccessTokenService kisApiAccessTokenService;
    private final StockRepository stockRepository;
    private final ConnectWebSocketClient client;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final StockOutlineService stockOutlineService;

    @Value("${KIS_APP_KEY}")
    private String appKey;

    @Value("${KIS_APP_SECRET}")
    private String appSecret;

    @Value("${REAL_BASE_URL}")
    private String baseUrl;

    private String approvalKey;
    private String accessToken;

    private static final List<String> FIXED_TICKERS = List.of(
            "005930", "000660", "373220", "207940", "005380", "068270", "000270", "005935", "005490", "105560",
            "028260", "055550", "035420", "000810", "012330", "066570", "051910", "006400", "086790", "032830",
            "010130", "329180", "035720", "015760", "003550", "034730", "011200", "018260", "009150", "034020",
            "010140", "024110", "096770", "042660", "012450", "316140", "001450", "267250", "033780", "000100");

    private static final Map<String, String> TICKER_NAME_MAP = Map.ofEntries(
            Map.entry("005930", "삼성전자"), Map.entry("000660", "SK하이닉스"), Map.entry("373220", "LG에너지솔루션"),
            Map.entry("207940", "삼성바이오로직스"), Map.entry("005380", "현대차"), Map.entry("068270", "셀트리온"),
            Map.entry("000270", "기아"), Map.entry("005935", "삼성전자우"), Map.entry("005490", "POSCO홀딩스"),
            Map.entry("105560", "KB금융"), Map.entry("028260", "삼성물산"), Map.entry("055550", "신한지주"),
            Map.entry("035420", "NAVER"), Map.entry("000810", "삼성화재"), Map.entry("012330", "현대모비스"),
            Map.entry("066570", "LG전자"), Map.entry("051910", "LG화학"), Map.entry("006400", "삼성SDI"),
            Map.entry("086790", "하나금융지주"), Map.entry("032830", "삼성생명"), Map.entry("010130", "고려아연"),
            Map.entry("329180", "HD현대중공업"), Map.entry("035720", "카카오"), Map.entry("015760", "한국전력"),
            Map.entry("003550", "LG"), Map.entry("034730", "SK"), Map.entry("011200", "HMM"),
            Map.entry("018260", "현대제철"), Map.entry("009150", "삼성전기"), Map.entry("034020", "두산에너빌리티"),
            Map.entry("010140", "삼성중공업"), Map.entry("024110", "기업은행"), Map.entry("096770", "SK이노베이션"),
            Map.entry("042660", "한화오션"), Map.entry("012450", "한화에어로스페이스"), Map.entry("316140", "우리금융지주"),
            Map.entry("001450", "현대해상"), Map.entry("267250", "HD현대일렉트릭"), Map.entry("033780", "KT&G"),
            Map.entry("000100", "유한양행"));

    // 서버 가동 시 고정된 40개 주식들 정보 가져와서 RDB 저장
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void saveFixedStocks() {
        approvalKey = approvalKeyService.getApprovalKey();
        accessToken = kisApiAccessTokenService.getAccessToken();

        log.info("서버 가동: 고정 종목 40개 정보 초기화 시작");
        for (String ticker : FIXED_TICKERS) {
            try {
                // RDB에 종목 기본 정보 저장 및 갱신
                Stock stock = stockRepository.findByTicker(ticker).orElse(null);
                String name = TICKER_NAME_MAP.getOrDefault(ticker, "알 수 없는 종목");
                String outline = stockOutlineService.getOutline(ticker);

                if (stock == null) {
                    stock = Stock.builder()
                            .ticker(ticker)
                            .name(name)
                            .market(Market.KOSPI)
                            .volume(0L)
                            .outline(outline)
                            .build();
                } else {
                    // 기존 종목이 있다면 이름과 개요 최신화
                    stock.setName(name);
                    if (outline != null) {
                        stock.setOutline(outline);
                    }
                }
                stockRepository.save(stock);

                // 최신 데이터(가격 등) 가져오기 및 Redis 저장
                getStockInfoRest(ticker);
            } catch (Exception e) {
                log.warn("종목 초기화 실패: {}", ticker, e);
            }
            try {
                Thread.sleep(1000); // KIS API 초당 호출 제한(TPS) 방지를 위해 무조건 1초 대기
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("서버 가동: 고정 종목 40개 정보 초기화 완료");
    }

    // 메인 화면에 표기될 전체 주식들 정보 얻기 위해 구독
    // @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void subscribeFixedStocks(ApplicationReadyEvent event) throws JsonProcessingException, InterruptedException {
        subscribeAllStocks();
    }

    /**
     * 평일 9시에 자동으로 종목 구독
     * cron 표현식: 초 분 시 일 월 요일
     * 0 0 9 * * MON-FRI: 평일 9시 0분 0초
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Seoul")
    public void subscribeStocksAtMarketOpen() throws JsonProcessingException, InterruptedException {
        log.info("평일 9시 스케줄러 실행 - 종목 구독 시작");
        if (MarketTime.isMarketOpen()) {
            subscribeAllStocks();
        } else {
            log.info("장 시간이 아니므로 구독하지 않습니다.");
        }
    }

    /**
     * 전체 종목 구독 로직
     */
    private void subscribeAllStocks() throws JsonProcessingException, InterruptedException {
        // 만약 장시간이라면 40개 종목들 구독해서 KIS WebSocket으로 실시간 체결가 가져오기
        if (MarketTime.isMarketOpen()) {
            for (String ticker : FIXED_TICKERS) {
                subscribeStock(ticker); // 해당 종목 구독
            }
        } else { // 만약 장외 시간이고 서버 첫 가동이서 데이터 없다면 종목들 데이터 받아오기
            for (String ticker : FIXED_TICKERS) {
                if (redisTemplate.hasKey("stock:data:" + ticker)) {
                    log.info("Redis에 기존 데이터 존재 → API 호출 생략: {}", ticker);
                    continue;
                }
                getStockInfoRest(ticker); // Rest API로 종가 가져오기
                Thread.sleep(1000); // TPS 제한 고려하여 1초 대기
            }
        }
    }

    // 주식 종목 구독
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

    // 서버 가동시 사이트에서 다룰 40개 주식 종목 최초 정보 가져오기
    public void getStockInfoRest(String trKey) {
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
                builder.toUriString(), HttpMethod.GET, entity, Map.class);

        try {
            Map body = response.getBody();
            if (body == null)
                return;

            Object output = body.get("output");
            Map<String, Object> om = (Map<String, Object>) output;

            int price = Integer.parseInt(Optional.ofNullable(om.get("stck_prpr")).orElse("0").toString());
            long changeAmount = Long.parseLong(Optional.ofNullable(om.get("prdy_vrss")).orElse("0").toString());
            double changeRate = Double.parseDouble(Optional.ofNullable(om.get("prdy_ctrt")).orElse("0").toString());
            long volume = Long.parseLong(Optional.ofNullable(om.get("acml_vol")).orElse("0").toString());

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

            String companyName = TICKER_NAME_MAP.getOrDefault(trKey, stockRepository.findNameByTicker(trKey));
            if (companyName == null)
                companyName = "알 수 없는 종목";

            ObjectNode out = mapper.createObjectNode();
            out.put("ticker", trKey); // 종목 코드
            out.put("price", price); // 현재가
            out.put("changeAmount", changeAmount); // 주가 변화
            out.put("changeRate", changeRate); // 등락률
            out.put("companyName", companyName); // 회사 이름
            out.put("volume", volume); // 거래량
            if (tradeTime != null) {
                out.put("tradeTime", tradeTime); // WebSocket에서 갱신된 값 유지
            }

            String json = mapper.writeValueAsString(out);

            redisTemplate.opsForValue().set(key, json); // Redis에 주가 정보 저장

            // 정렬용 ZSET 업데이트
            redisTemplate.opsForZSet().add("stock:rank:volume", trKey, volume); // 거래량 많은 순으로 정렬 redis 저장
            redisTemplate.opsForZSet().add("stock:rank:price", trKey, price); // 가격 높은 순으로 정렬 redis 저장
            redisTemplate.opsForZSet().add("stock:rank:changeRate", trKey, changeRate); // 등락률 높은 순으로 정렬 redis 저장

            // STOMP로 직접 전송
            messagingTemplate.convertAndSend("/topic/stocks", json);

            log.info("Redis 저장 & STOMP 직접 전송(REST) → {}", json);

        } catch (Exception e) {
            log.warn("REST 현재가 브로드캐스트 실패", e);
        }
    }

}
