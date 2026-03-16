package com.project.demo.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.kis.AesDecryptUtil;
import com.project.demo.common.kis.KisApprovalKeyService;
import com.project.demo.common.util.MarketTime;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.stock.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Slf4j
@Component
public class ConnectWebSocketClient extends WebSocketClient {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final StockRepository stockRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderService orderService;
    private final KisApprovalKeyService approvalKeyService;

    private String iv;
    private String key;
    private String approvalKey;
    private List<String> tickers;

    public ConnectWebSocketClient(ObjectMapper objectMapper, StringRedisTemplate redisTemplate,
                                StockRepository stockRepository, SimpMessagingTemplate messagingTemplate,
                                OrderService orderService, KisApprovalKeyService approvalKeyService,
                                @Value("${kis.url.ws}") String wsUrl)
            throws Exception {
        super(new URI(wsUrl));
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.stockRepository = stockRepository;
        this.messagingTemplate = messagingTemplate;
        this.orderService = orderService;
        this.approvalKeyService = approvalKeyService;
    }

    /**
     * Spring Boot 실행되면 자동 연결 시도
     */
    @PostConstruct
    public void init() {
        tryConnect();
    }

    public void tryConnect() {
        if (this.isOpen()) return;

        new Thread(() -> {
            try {
                if (MarketTime.isMarketOpen()) {
                    log.info("장 시간 → WebSocket 연결 시도 중...");
                    // 연결 직전에 신선한 Approval Key를 가져온다 (만료 시 자동 갱신됨)
                    this.approvalKey = approvalKeyService.getApprovalKey();
                    this.connectBlocking();
                } else {
                    log.info("장 외 시간 → WebSocket 연결 대기");
                }
            } catch (Exception e) {
                log.error("WebSocket 연결 시도 실패", e);
            }
        }).start();
    }

    public void setSubscriptionInfo(String approvalKey, List<String> tickers) {
        // approvalKeyService를 통해 직접 관리하므로 매개변수로 넘어온 approvalKey는 무시해도 될 수 있지만 하위 호환성을 위해 유지
        if (approvalKey != null) {
            this.approvalKey = approvalKey;
        }
        this.tickers = tickers;
        if (this.isOpen()) {
            subscribeAll();
        }
    }

    private void subscribeAll() {
        if (approvalKey == null || tickers == null) {
            log.warn("구독 정보(Approval Key 또는 Tickers)가 없어 구독을 생략합니다.");
            return;
        }
        log.info("전체 종목 구독 시작 (개수: {})", tickers.size());
        for (String ticker : tickers) {
            try {
                subscribeStock(ticker);
            } catch (Exception e) {
                log.error("종목 구독 실패: {}", ticker, e);
            }
        }
    }

    private void subscribeStock(String ticker) throws Exception {
        ObjectNode header = objectMapper.createObjectNode();
        header.put("approval_key", approvalKey);
        header.put("custtype", "P");
        header.put("tr_type", "1");
        header.put("content-type", "utf-8");

        ObjectNode input = objectMapper.createObjectNode();
        input.put("tr_id", "H0STCNT0");
        input.put("tr_key", ticker);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("input", input);

        ObjectNode request = objectMapper.createObjectNode();
        request.set("header", header);
        request.set("body", body);

        String json = objectMapper.writeValueAsString(request);
        this.send(json);
    }

    /**
     * 장 마감 15:20에 1회 실행 → WebSocket 종료
     */
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void closeAtMarketClose() {
        if (this.isOpen()) {
            log.info("장 마감 시각 도달 → WebSocket 연결 종료");
            this.close();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("KIS WebSocket 서버와 연결됨: {}", handshake.getHttpStatusMessage());
        subscribeAll();
    }

    @Override
    public void onMessage(String message) {
        try {
            if (message.startsWith("{")) {
                var json = objectMapper.readTree(message);
                if (json.has("body") && json.get("body").has("output")) {
                    this.iv = json.get("body").get("output").get("iv").asText();
                    this.key = json.get("body").get("output").get("key").asText();
                    log.info("iv={}, key={}", iv, key);
                }
            } else {
                receiveRealTimeData(message);
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패", e);
        }
    }

    private void receiveRealTimeData(String message) throws Exception {
        String[] parts = message.split("\\|");
        String encFlag = parts[0];
        String trId = parts[1];
        String data = parts[3];

        if ("1".equals(encFlag)) {
            String decrypted = AesDecryptUtil.decrypt(data, key, iv);
            log.info("복호화 결과: {}", decrypted);
        } else {
            String[] fields = data.split("\\^");

            String ticker = fields[0];
            String tradeTime = fields[1];
            int price = Integer.parseInt(fields[2]);
            double changeAmount = Double.parseDouble(fields[4]);
            double changeRate = Double.parseDouble(fields[5]);
            long volume = Long.parseLong(fields[13]);
            String companyName = stockRepository.findNameByTicker(ticker);

            ObjectNode out = objectMapper.createObjectNode();
            out.put("ticker", ticker);
            out.put("price", price);
            out.put("changeAmount", changeAmount);
            out.put("changeRate", changeRate);
            out.put("companyName", companyName);
            out.put("tradeTime", tradeTime);
            out.put("volume", volume);

            String json = objectMapper.writeValueAsString(out);

            redisTemplate.opsForValue().set("stock:data:" + ticker, json);
            redisTemplate.opsForZSet().add("stock:rank:volume", ticker, volume);
            redisTemplate.opsForZSet().add("stock:rank:price", ticker, price);
            redisTemplate.opsForZSet().add("stock:rank:changeRate", ticker, changeRate);

            messagingTemplate.convertAndSend("/topic/stocks", json);
            log.info("Redis 저장 & STOMP 직접 전송(WS) → {}", json);

            try {
                orderService.executeReservedOrdersForTicker(ticker, price);
            } catch (Exception e) {
                log.error("예약 주문 체결 중 오류 발생 - 종목: {}, 현재가: {}, 오류: {}", ticker, price, e.getMessage(), e);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket 연결 종료. code={}, reason={}, remote={}", code, reason, remote);
        if (MarketTime.isMarketOpen()) {
            log.info("장 시간 중 연결 종료 → 재연결 루프 시작...");
            scheduleReconnection();
        }
    }

    private void scheduleReconnection() {
        new Thread(() -> {
            try {
                while (!this.isOpen() && MarketTime.isMarketOpen()) {
                    log.info("5초 후 WebSocket 재연결 시도...");
                    Thread.sleep(5000);
                    try {
                        // reconnectBlocking() 호출 전에도 key 갱신 시도
                        this.approvalKey = approvalKeyService.getApprovalKey();
                        this.reconnectBlocking();
                        if (this.isOpen()) {
                            log.info("WebSocket 재연결 성공");
                            break;
                        }
                    } catch (Exception e) {
                        log.error("WebSocket 재연결 시도 중 오류 발생", e);
                    }
                }
            } catch (InterruptedException e) {
                log.error("재연결 루프 중단", e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket 에러 발생", ex);
    }
}