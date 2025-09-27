package com.project.demo.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.oauth.service.AesDecryptUtil;
import com.project.demo.common.time.MarketTime;
import com.project.demo.domain.stock.service.StockBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.*;

import static com.project.demo.common.time.MarketTime.KST;

@Slf4j
@Component
public class ConnectWebSocketClient extends WebSocketClient {

    private final StockBroadcastService broadcastService;
    private final ObjectMapper objectMapper;

    private String iv;
    private String key;

    public ConnectWebSocketClient(StockBroadcastService broadcastService, ObjectMapper objectMapper) throws Exception {
        super(new URI("ws://ops.koreainvestment.com:21000")); // 실전투자 도메인
        this.broadcastService = broadcastService;
        this.objectMapper = objectMapper;
    }

    /** Spring Boot 실행되면 자동 연결 */
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

    /** 장 종료 감시 → 1분마다 실행 */
    @Scheduled(cron = "0 * 9-16 * * MON-FRI", zone = "Asia/Seoul")
    public void checkMarketClose() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        if (!MarketTime.isMarketOpenAt(now) && this.isOpen()) {
            log.info("장 마감 감지 → WebSocket 연결 종료");
            this.close();
        }
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("서버와 연결됨: {}", handshake);
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
                receiveRealTimeDomestic(message);
                broadcastService.broadcast(message);
            }
        } catch (Exception e) {
            log.error("메시지 처리 실패", e);
        }
    }

    private void receiveRealTimeDomestic(String message) throws Exception {
        String[] parts = message.split("\\|");
        String encFlag = parts[0];
        String trId = parts[1];
        String data = parts[3];

        if ("1".equals(encFlag)) {
            String decrypted = AesDecryptUtil.decrypt(data, key, iv);
            log.info("복호화 결과: {}", decrypted);
        } else {
            String[] fields = data.split("\\^");
            log.info("종목코드={}, 체결시간={}, 현재가={}", fields[0], fields[1], fields[2]);
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

//    // === 장 시간 계산 로직 ===
//    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
//    private static final LocalTime REGULAR_OPEN  = LocalTime.of(9, 0);
//    private static final LocalTime REGULAR_CLOSE = LocalTime.of(15, 30);
//
//    public boolean isMarketOpen() {
//        return isMarketOpenAt(ZonedDateTime.now(KST));
//    }
//
//    public boolean isMarketOpenAt(ZonedDateTime when) {
//        boolean isWeekday = isWeekday(when.toLocalDate());
//        boolean inRegularHours = isInRegularSession(when.toLocalTime());
//        boolean isHoliday = isKrxHoliday(when.toLocalDate());
//        return isWeekday && !isHoliday && inRegularHours;
//    }
//
//    private boolean isWeekday(LocalDate date) {
//        DayOfWeek dow = date.getDayOfWeek();
//        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
//    }
//
//    private boolean isInRegularSession(LocalTime time) {
//        return !time.isBefore(REGULAR_OPEN) && !time.isAfter(REGULAR_CLOSE);
//    }
//
//    private boolean isKrxHoliday(LocalDate date) {
//        return false; // 추후 확장 가능
//    }
}
