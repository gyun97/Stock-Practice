package com.project.demo.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.kis.AesDecryptUtil;
import com.project.demo.common.util.MarketTime;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.stock.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;

@Slf4j
@Component
public class ConnectWebSocketClient extends WebSocketClient {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final StockRepository stockRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderService orderService;

    private String iv;
    private String key;

    public ConnectWebSocketClient(ObjectMapper objectMapper, StringRedisTemplate redisTemplate,
            StockRepository stockRepository, SimpMessagingTemplate messagingTemplate, OrderService orderService)
            throws Exception {
        super(new URI("ws://ops.koreainvestment.com:21000")); // 실전투자 도메인
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.stockRepository = stockRepository;
        this.messagingTemplate = messagingTemplate;
        this.orderService = orderService;
    }

    /**
     * Spring Boot 실행되면 자동 연결
     */
    @PostConstruct
    public void init() {
        new Thread(() -> {
            try {
                if (MarketTime.isMarketOpen()) {
                    log.info("장 시간 → WebSocket 연결 시도 중...");
                    this.connectBlocking();
                    log.info("WebSocket 연결 성공");
                } else {
                    log.info("장 외 시간 → WebSocket 연결하지 않음");
                }
            } catch (Exception e) {
                log.error("WebSocket 연결 실패", e);
            }
        }).start();
    }

    /**
     * 장 마감 15:20에 1회 실행 → WebSocket 종료
     */
    @Scheduled(cron = "0 20 15 * * MON-FRI", zone = "Asia/Seoul")
    public void closeAtMarketClose() {
        if (this.isOpen()) {
            log.info("장 마감 시각 도달 → WebSocket 연결 종료");
            this.close();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("서버와 연결됨: {}", handshake);
    }

    // 메시지가 수신되었을 때 자동으로 특정 메서드를 실행하는 콜백(callback) 메서드
    @Override
    public void onMessage(String message) {
        try {
            if (message.startsWith("{")) {
                var json = objectMapper.readTree(message);
                if (json.has("body") && json.get("body").has("output")) {
                    this.iv = json.get("body").get("output").get("iv").asText(); // iv: 실시간 결과 복호화에 필요한
                                                                                 // AES256((Initialize Vector))
                    this.key = json.get("body").get("output").get("key").asText(); // key: 실시간 결과 복호화에 필요한 AES256 Key
                    log.info("iv={}, key={}", iv, key);
                }
            } else {
                receiveRealTimeData(message); // 웹소켓 실시간 주식 데이터 전처리 및 Redis 저장
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패", e);
        }
    }

    // 실시간 주식 데이터 수신
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

            String ticker = fields[0]; // 종목 코드
            String tradeTime = fields[1];
            int price = Integer.parseInt(fields[2]);
            double changeAmount = Double.parseDouble(fields[4]); // 주가 변화
            double changeRate = Double.parseDouble(fields[5]); // 등락률
            long volume = Long.parseLong(fields[13]); // 누적 거래량
            String companyName = stockRepository.findNameByTicker(ticker);

            ObjectNode out = objectMapper.createObjectNode();
            out.put("ticker", ticker);
            out.put("price", price);
            out.put("changeAmount", changeAmount);
            out.put("changeRate", changeRate);
            out.put("companyName", companyName);
            out.put("tradeTime", tradeTime); // WebSocket만 tradeTime 갱신
            out.put("volume", volume);

            String json = objectMapper.writeValueAsString(out);

            redisTemplate.opsForValue().set("stock:data:" + ticker, json); // Redis에 실시간 해당 종목 데이터 저장
            redisTemplate.opsForZSet().add("stock:rank:volume", ticker, volume); // 거래량 많은 순으로 정렬 redis 저장
            redisTemplate.opsForZSet().add("stock:rank:price", ticker, price); // 가격 높은 순으로 정렬 redis 저장
            redisTemplate.opsForZSet().add("stock:rank:changeRate", ticker, changeRate); // 등락률 높은 순으로 정렬 redis 저장
            // STOMP로 직접 전송
            messagingTemplate.convertAndSend("/topic/stocks", json);

            log.info("Redis 저장 & STOMP 직접 전송(WS) → {}", json);

            // 이벤트 기반 예약 주문 체결 (주가 업데이트 시 해당 종목의 예약 주문만 체크)
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
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket 에러 발생", ex);
    }
}