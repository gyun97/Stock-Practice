package com.project.demo.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.oauth.service.AesDecryptUtil;
import com.project.demo.domain.stock.service.StockBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import java.net.URI;

@Slf4j
@Component
public class ConnectWebSocketClient extends WebSocketClient {


    private StockBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    // 구독 응답에서 내려오는 복호화 값 저장용 필드
    private String iv;
    private String key;

    public ConnectWebSocketClient(StockBroadcastService broadcastService, ObjectMapper objectMapper) throws Exception {
        super(new URI("ws://ops.koreainvestment.com:31000/tryitout")); // 모의투자 도메인
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    /** Spring Boot 실행되면 자동 연결 */
    @PostConstruct
    public void init() {
        new Thread(() -> {
            try {
                log.info("WebSocket 연결 시도 중...");
                this.connectBlocking(); // 연결 완료될 때까지 대기
                log.info("WebSocket 연결 성공");
            } catch (Exception e) {
                log.error("WebSocket 연결 실패", e);
            }
        }).start();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("서버와 연결됨: {}", handshake);
    }

    @Override
    public void onMessage(String message) {
        try {
            if (message.startsWith("{")) {
                // JSON → 구독 응답 (SUBSCRIBE SUCCESS)
//                log.info("수신 메시지: {}", message);
                var json = objectMapper.readTree(message);
                if (json.has("body") && json.get("body").has("output")) {
                    this.iv = json.get("body").get("output").get("iv").asText();
                    this.key = json.get("body").get("output").get("key").asText();
                    log.info("iv={}, key={}", iv, key);
                }
            } else {
                // 실시간 데이터 응답
                receiveRealTimeDomestic(message);
                broadcastService.broadcast(message); // 프론트로 주식 실시간 데이터 전송
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패", e);
        }

    }

    // KIS 웹소켓으로 실시간 국내 주식 응답 데이터
    private void receiveRealTimeDomestic(String message) throws Exception {
        String[] parts = message.split("\\|");
        String encFlag = parts[0]; // 암호화 여부(0 or 1)
        String trId = parts[1]; // 종목코드
        String count = parts[2];
        String data = parts[3];

        if ("1".equals(encFlag)) {
            // 암호화된 경우
            String decrypted = AesDecryptUtil.decrypt(data, key, iv);
            log.info("복호화 결과: {}", decrypted);
        } else {
            // 암호화 안 된 경우 → "^" 기준 split
            String[] fields = data.split("\\^");
            log.info("종목코드={}, 체결시간={}, 현재가={}",
                    fields[0], fields[1], fields[2]);
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
