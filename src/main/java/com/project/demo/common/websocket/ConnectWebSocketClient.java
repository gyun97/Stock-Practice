package com.project.demo.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.oauth.service.AesDecryptUtil;
import com.project.demo.common.time.MarketTime;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.stock.service.StockBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;

@Slf4j
@Component
public class ConnectWebSocketClient extends WebSocketClient {

    private final StockBroadcastService broadcastService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final StockRepository stockRepository;

    private String iv;
    private String key;

    public ConnectWebSocketClient(StockBroadcastService broadcastService, ObjectMapper objectMapper, StringRedisTemplate redisTemplate, StockRepository stockRepository) throws Exception {
        super(new URI("ws://ops.koreainvestment.com:21000")); // 실전투자 도메인
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.stockRepository = stockRepository;
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
     * 장 마감 직후 15:31에 1회 실행 → WebSocket 종료
     */
    @Scheduled(cron = "0 31 15 * * MON-FRI", zone = "Asia/Seoul")
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
                    this.iv = json.get("body").get("output").get("iv").asText(); // iv: 실시간 결과 복호화에 필요한 AES256((Initialize Vector))
                    this.key = json.get("body").get("output").get("key").asText(); // key: 실시간 결과 복호화에 필요한 AES256 Key
                    log.info("iv={}, key={}", iv, key);
                }
            } else {
                receiveRealTimeData(message); // 웹소켓 실시간 주식 데이터 전처리 및 Redis 저장
                broadcastService.broadcast(message); // 프론트 엔드에 Redis Pub/Sub을 통해 실시간 데이터 전송

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

            String ticker = fields[0];
            String tradeTime = fields[1];
            double price = Double.parseDouble(fields[2]);
            double changeAmount = Double.parseDouble(fields[4]);
            double changeRate = Double.parseDouble(fields[5]);
            String companyName = stockRepository.findNameByTicker(ticker);

            ObjectNode out = objectMapper.createObjectNode();
            out.put("stockCode", ticker);
            out.put("price", price);
            out.put("changeAmount", changeAmount);
            out.put("changeRate", changeRate);
            out.put("companyName", companyName);
            out.put("tradeTime", tradeTime); // WebSocket만 tradeTime 갱신

            String json = objectMapper.writeValueAsString(out);

            redisTemplate.opsForValue().set("stock:data:" + ticker, json);
            redisTemplate.convertAndSend("stock:updates", json);

            log.info("Redis 저장 & Pub/Sub 발행(WS) → {}", json);
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