package com.project.demo.common.websocket.netty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.kis.KisApprovalKeyService;
import com.project.demo.common.redis.RedisStreamProducer;
import com.project.demo.domain.stock.service.StockMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 메시지 수신 핸들러.
 *
 * <pre>
 * 역할 (Single Responsibility):
 *  - PINGPONG 응답
 *  - Approval Key 만료 감지 → 키 갱신 후 채널 닫기 (재연결은 KisNettyWebSocketClient가 담당)
 *  - 암호화 키(IV/KEY) 캐싱
 *  - 실시간 주가 데이터 → Redis Streams XADD (Producer만 수행, 처리 로직 없음)
 *  - Idle 감지 → 채널 강제 종료 (재연결 트리거)
 * </pre>
 *
 * <p>
 * <b>중요:</b> 이 핸들러는 @Sharable로 여러 채널에서 재사용됩니다.
 * 상태(iv, key)는 채널 재연결 시 초기화되어야 하므로 volatile로 관리합니다.
 * </p>
 */
@Slf4j
@Component
@io.netty.channel.ChannelHandler.Sharable
@RequiredArgsConstructor
public class KisWebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final ObjectMapper objectMapper;
    private final KisApprovalKeyService approvalKeyService;
    private final RedisStreamProducer redisStreamProducer;
    private final StockMetrics stockMetrics;

    /** AES 복호화 키 (구독 확인 응답 시 서버가 전달) */
    private volatile String iv;
    private volatile String key;

    // ──────────────────────────────────────────────────────────
    // WebSocket 프레임 수신
    // ──────────────────────────────────────────────────────────

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            // Binary frame 등 무시
            return;
        }

        String message = textFrame.text();

        // 실시간 데이터 수신 기록 (Ingress - 네트워크 인입 즉시)
        if (stockMetrics != null) {
            stockMetrics.recordRealtimeReceived();
        }

        if (message.startsWith("{")) {
            handleJsonMessage(ctx, message);
        } else {
            // 파이프(|) 구분 실시간 주가 데이터 → Redis Streams에 즉시 적재 후 반환
            handleRawStockData(message);
        }
    }

    /**
     * JSON 메시지 처리 (PINGPONG, 구독 응답, 오류 응답).
     */
    private void handleJsonMessage(ChannelHandlerContext ctx, String message) throws Exception {
        JsonNode json = objectMapper.readTree(message);

        // 1) PINGPONG 응답
        if (json.has("header")) {
            String trId = json.path("header").path("tr_id").asText();
            if ("PINGPONG".equals(trId)) {
                log.debug("PINGPONG 수신 → 에코 응답");
                ctx.channel().writeAndFlush(new TextWebSocketFrame(message));
                return;
            }
        }

        // 2) 구독 응답 body 처리
        if (json.has("body")) {
            JsonNode body = json.get("body");

            // 오류 응답 (Approval Key 만료 등)
            if (body.has("rt_cd") && !"0".equals(body.get("rt_cd").asText())) {
                String msgCd = body.path("msg_cd").asText("");
                String msg1 = body.path("msg1").asText("");

                log.warn("KIS 오류 응답 수신: rt_cd={} msg_cd={} msg1={}",
                        body.get("rt_cd").asText(), msgCd, msg1);

                if ("EGW00123".equals(msgCd) || "OPSP0002".equals(msgCd)
                        || "OPSP0007".equals(msgCd) || "OPSP0011".equals(msgCd) || "OPSP8996".equals(msgCd)
                        || msg1.contains("승인키") || msg1.contains("만료") || msg1.contains("유효하지")
                        || msg1.toLowerCase().contains("invalid approval")
                        || msg1.contains("ALREADY IN USE")) {
                    log.error(
                            "[데이터 중단 원인 발생] Approval Key 만료 또는 다른 서버와 충돌 감지(msg_cd={}). KIS 서버가 실시간 데이터 전송을 중단했습니다. 키 강제 갱신 후 채널을 재연결합니다.",
                            msgCd);
                    approvalKeyService.refreshApprovalKey();
                    ctx.close(); // onClose → 재연결 루프 실행
                }
                return;
            }

            // 암호화 키 수신 (구독 확인 응답)
            JsonNode output = body.path("output");
            if (output.has("iv") && output.has("key")) {
                this.iv = output.get("iv").asText();
                this.key = output.get("key").asText();
                log.info("AES 암호화 키 수신 완료 (iv={}, key={})", iv, key);
            }
        }
    }

    /**
     * 파이프 구분 실시간 데이터 → Redis Streams XADD.
     * 이 메서드는 절대 블로킹 I/O를 수행하지 않습니다.
     */
    // 패킷 로그 출력용 카운터 및 마지막 로그 시각 (5분 주기 로그용)
    private final AtomicInteger receiveCounter = new AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicLong lastLogTime =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    private static final long LOG_INTERVAL_MS = 5 * 60 * 1_000L; // 5분

    private void handleRawStockData(String rawMessage) {
        int count = receiveCounter.incrementAndGet();

        // 5분마다 수신 현황 로그 출력 (카운터 리셋하여 실제 5분 단위 건수 표시)
        long now = System.currentTimeMillis();
        long last = lastLogTime.get();
        if (now - last >= LOG_INTERVAL_MS && lastLogTime.compareAndSet(last, now)) {
            int snapshot = receiveCounter.getAndSet(0); // 리셋하여 다음 5분 카운트 초기화
            log.info("[실시간 데이터 정상 수신 중] 최근 5분간 수신 패킷: {}건 ({}건/초)", snapshot, snapshot / 300);
        }

        String[] parts = rawMessage.split("\\|");
        if (parts.length < 4) {
            log.warn("올바르지 않은 실시간 데이터 형식 (parts < 4): {}", rawMessage);
            return;
        }

        String encFlag = parts[0];
        String data = parts[3];

        // 암호화 데이터는 IV/KEY가 준비된 경우에만 적재
        if ("1".equals(encFlag)) {
            if (iv == null || key == null) {
                log.warn("암호화 데이터 수신이나 IV/KEY 미수신 상태 → 스킵");
                return;
            }
            // 암호화된 원문 그대로 스트림에 적재 (복호화는 Consumer에서 수행)
            redisStreamProducer.publishEncrypted(data, iv, key);
        } else {
            // 종목코드 추출 (fields[0])
            String[] fields = data.split("\\^");
            if (fields.length < 1)
                return;
            String ticker = fields[0];

            // Redis Streams에 XADD (논블로킹)
            redisStreamProducer.publish(ticker, data);
        }
    }

    // ──────────────────────────────────────────────────────────
    // 연결 상태 이벤트
    // ──────────────────────────────────────────────────────────

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.warn("60초간 KIS로부터 메시지 수신 없음 (Stale Connection) → 채널 강제 종료하여 재연결 유도");
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("Netty 채널 비활성화 (연결 종료). KisNettyWebSocketClient가 재연결을 처리합니다.");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Netty 채널 예외 발생: {}", cause.getMessage(), cause);
        ctx.close();
    }

    /** 재연결 시 암호화 키 초기화 */
    public void resetEncryptionKeys() {
        this.iv = null;
        this.key = null;
    }
}
