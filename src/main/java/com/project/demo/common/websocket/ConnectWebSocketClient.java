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
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private volatile long lastMessageTimestamp = System.currentTimeMillis();

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
        this.setConnectionLostTimeout(0);
    }

    /**
     * Spring Boot 실행되면 자동 연결 시도
     */
    @PostConstruct
    public void init() {
        tryConnect();
    }

    public void tryConnect() {
        if (this.isOpen())
            return;

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
        if (approvalKey != null) {
            this.approvalKey = approvalKey;
        }
        this.tickers = tickers;
        if (this.isOpen()) {
            subscribeAll();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("KIS WebSocket 서버와 연결됨: {}", handshake.getHttpStatusMessage());
        
        // 구독 프로세스를 별도 스레드에서 실행하여 WebSocket 스레드 블로킹 방지
        new Thread(this::subscribeAll).start();
    }

    private void subscribeAll() {
        if (tickers == null || tickers.isEmpty()) {
            return;
        }

        log.info("전체 종목 구독 시작 (총 {}종목)", tickers.size());
        for (String ticker : tickers) {
            if (!this.isOpen()) {
                log.warn("WebSocket 연결이 닫혀 있어 구독을 중단합니다. (종목: {})", ticker);
                break;
            }
            
            try {
                subscribeStock(ticker);
                // KIS 가이드에 따라 요청 간 간격 유지
                Thread.sleep(100); 
            } catch (WebsocketNotConnectedException e) {
                log.error("구독 중 연결 끊김 감지: {}", e.getMessage());
                break;
            } catch (InterruptedException e) {
                log.error("구독 루프 인터럽트 발생", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("종목 구독 실패: {}", ticker, e);
            }
        }
        log.info("전체 종목 구독 프로세스 완료");
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

    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void closeAtMarketClose() {
        if (this.isOpen()) {
            log.info("장 마감 시각 도달 → WebSocket 연결 종료");
            this.close();
        }
    }

    /**
     * 30초마다 연결 상태 확인 (스태일 커넥션 감지)
     */
    @Scheduled(fixedDelay = 30000)
    public void checkConnectionHealth() {
        if (!MarketTime.isMarketOpen()) {
            return;
        }

        if (this.isOpen()) {
            long now = System.currentTimeMillis();
            long diff = now - lastMessageTimestamp;

            if (diff > 60000) { // 60초 넘게 메시지가 없으면
                log.warn("WebSocket 연결은 유지 중이나 60초간 메시지 수신 없음 (Stale). 강제 재연결 시도. (마지막 수신: {}ms 전)", diff);
                this.close(); // onClose()가 호출되어 재연결 루프가 실행됨
            } else {
                log.info("WebSocket 연결 건강함 (최근 메시지 수신: {}ms 전)", diff);
            }
        } else {
            log.info("WebSocket 연결이 닫혀있음 (장 중). 재연결 시도 중일 수 있음.");
        }
    }


    @Override
    public void onMessage(String message) {
        this.lastMessageTimestamp = System.currentTimeMillis();
        try {
            if (message.startsWith("{")) {
                var json = objectMapper.readTree(message);

                if (json.has("header") && json.get("header").has("tr_id")) {
                    String trId = json.get("header").get("tr_id").asText();
                    if ("PINGPONG".equals(trId)) {
                        log.info("PINGPONG 메시지 수신, 에코 응답 전송: {}", message);
                        this.send(message);
                        return;
                    }
                }

                if (json.has("body") && json.get("body").has("output")) {
                    this.iv = json.get("body").get("output").get("iv").asText();
                    this.key = json.get("body").get("output").get("key").asText();
                    log.info("iv={}, key={}", iv, key);
                }
            } else {
                receiveRealTimeData(message);
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패 (메시지: {}): {}", message, e.getMessage(), e);
        }
    }

    private void receiveRealTimeData(String message) throws Exception {
        String[] parts = message.split("\\|");
        if (parts.length < 4) {
            log.warn("올바르지 않은 실시간 데이터 형식 (parts < 4): {}", message);
            return;
        }
        
        String encFlag = parts[0];
        String data = parts[3];

        if ("1".equals(encFlag)) {
            String decrypted = AesDecryptUtil.decrypt(data, key, iv);
            log.info("복호화 결과: {}", decrypted);
        } else {
            String[] fields = data.split("\\^");
            if (fields.length < 14) {
                log.warn("올바르지 않은 실시간 데이터 필드 개수 (fields < 14): {}", data);
                return;
            }

            try {
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

                try {
                    redisTemplate.opsForValue().set("stock:data:" + ticker, json);
                    redisTemplate.opsForZSet().add("stock:rank:volume", ticker, volume);
                    redisTemplate.opsForZSet().add("stock:rank:price", ticker, price);
                    redisTemplate.opsForZSet().add("stock:rank:changeRate", ticker, changeRate);
                } catch (Exception e) {
                    log.error("Redis 저장 중 오류 발생 - 종목: {}, 오류: {}", ticker, e.getMessage());
                }

                try {
                    messagingTemplate.convertAndSend("/topic/stocks", json);
                } catch (Exception e) {
                    log.error("STOMP 전송 중 오류 발생 - 종목: {}, 오류: {}", ticker, e.getMessage());
                }

                log.info("실시간 주가 처리 완료 (Redis & STOMP) → {}", json);

                try {
                    orderService.executeReservedOrdersForTicker(ticker, price);
                } catch (Exception e) {
                    log.error("예약 주문 체결 중 오류 발생 - 종목: {}, 현재가: {}, 오류: {}", ticker, price, e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("실시간 데이터 파싱 중 상세 오류 발생 (data: {}): {}", data, e.getMessage());
            }
        }
    }

    private volatile long lastConnectTimestamp = 0;

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket 연결 종료. code={}, reason={}, remote={}", code, reason, remote);
        
        // 연결이 너무 빨리 끊겼거나(예: 5초 이내), 서버에 의해 끊긴 경우(원격 종료) 키가 부정확할 가능성이 큼
        long connectedDuration = System.currentTimeMillis() - lastConnectTimestamp;
        boolean isInstantDrop = connectedDuration < 5000;
        
        if (MarketTime.isMarketOpen()) {
            if (remote || isInstantDrop) {
                log.warn("빠른 연결 끊김 또는 서버 강제 종료 감지 (유지시간: {}ms). 다음 재연결 시 Approval Key를 강제 갱신합니다.", connectedDuration);
                approvalKeyService.refreshApprovalKey();
            }
            log.info("장 시간 중 연결 종료 → 재연결 루프 시작...");
            scheduleReconnection();
        }
    }

    private void scheduleReconnection() {
        if (!isReconnecting.compareAndSet(false, true)) {
            log.info("이미 재연결이 진행 중입니다. 중복 실행 방지.");
            return;
        }

        new Thread(() -> {
            try {
                int retryCount = 0;
                while (!this.isOpen() && MarketTime.isMarketOpen()) {
                    // 재시도 횟수에 따른 지연 시간 증가 (최대 30초)
                    long delay = Math.min(5000 + (retryCount * 5000), 30000);
                    log.info("{}초 후 WebSocket 재연결 시도... (시도 횟수: {})", delay / 1000, retryCount + 1);
                    Thread.sleep(delay);
                    
                    try {
                        this.approvalKey = approvalKeyService.getApprovalKey();
                        this.lastConnectTimestamp = System.currentTimeMillis();
                        this.reconnectBlocking();
                        if (this.isOpen()) {
                            log.info("WebSocket 재연결 성공");
                            break;
                        }
                    } catch (Exception e) {
                        log.error("WebSocket 재연결 시도 중 오류 발생", e);
                    }
                    retryCount++;
                }
            } catch (InterruptedException e) {
                log.error("재연결 루프 중단", e);
                Thread.currentThread().interrupt();
            } finally {
                isReconnecting.set(false);
            }
        }).start();
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket 에러 발생", ex);
    }
}