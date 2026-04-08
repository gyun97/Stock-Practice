package com.project.demo.common.websocket.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.kis.KisApprovalKeyService;
import com.project.demo.common.util.MarketTime;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.stock.service.StockMetrics;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty 기반 KIS WebSocket 클라이언트.
 *
 * <pre>
 * 역할 분담:
 *  - 이 클래스: 연결 생명주기 관리 (연결/재연결/구독/장마감 종료)
 *  - KisWebSocketHandler: 메시지 수신 및 Redis Streams 적재 (Producer)
 *  - RedisStreamConsumer: 비동기 처리 (STOMP 브로드캐스트, 주문 체결)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisNettyWebSocketClient {

    private final ObjectMapper objectMapper;
    private final KisApprovalKeyService approvalKeyService;
    private final StockRepository stockRepository;
    private final StockMetrics stockMetrics;
    private final KisWebSocketHandler kisWebSocketHandler;

    @Value("${kis.url.ws}")
    private String wsUrl;

    // Netty 리소스
    private NioEventLoopGroup eventLoopGroup;
    private Channel channel;

    // 구독 상태
    private String approvalKey;
    private List<String> tickers;

    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);

    // ──────────────────────────────────────────────────────────
    // 라이프사이클
    // ──────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        // Event Loop Group은 한 번만 생성 (재연결 시 재사용)
        this.eventLoopGroup = new NioEventLoopGroup(2); // 수신용 NIO 스레드 2개
        tryConnect();
    }

    @PreDestroy
    public void destroy() {
        log.info("애플리케이션 종료 → Netty 리소스 정리");
        closeChannel();
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
        }
    }

    // ──────────────────────────────────────────────────────────
    // 연결
    // ──────────────────────────────────────────────────────────

    public void tryConnect() {
        if (isChannelOpen()) return;

        new Thread(() -> {
            if (!MarketTime.isMarketOpen()) {
                log.info("장 외 시간 → WebSocket 연결 대기");
                return;
            }
            log.info("KIS WebSocket 연결 시도 중... ({})", wsUrl);
            doConnect();
        }, "kis-connect-thread").start();
    }

    private void doConnect() {
        if (isChannelOpen()) {
            log.info("이미 KIS WebSocket 연결이 활성화되어 있습니다. 연결 시도를 중단합니다.");
            return;
        }
        try {
            URI uri = new URI(wsUrl);
            this.approvalKey = approvalKeyService.getApprovalKey();

            SslContext sslContext = buildSslContext(uri);

            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new KisWebSocketInitializer(uri, kisWebSocketHandler, sslContext));

            int port = uri.getPort() != -1 ? uri.getPort()
                    : ("wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);

            ChannelFuture future = bootstrap.connect(uri.getHost(), port).sync();
            this.channel = future.channel();

            // 핸드쉐이크 완료 대기 (최대 10초)
            kisWebSocketHandler.resetEncryptionKeys();
            log.info("KIS WebSocket 연결 성공");

            // 연결 직후 구독
            scheduleSubscription();

            // 채널 종료 시 재연결 트리거
            channel.closeFuture().addListener(closeFuture -> {
                log.warn("Netty 채널 종료됨 → 재연결 스케줄링");
                stockMetrics.setSubscribeCount(0);
                if (MarketTime.isMarketOpen()) {
                    scheduleReconnection();
                }
            });

        } catch (Exception e) {
            log.error("KIS WebSocket 연결 실패: {}", e.getMessage(), e);
            if (MarketTime.isMarketOpen()) {
                scheduleReconnection();
            }
        }
    }

    /** 연결 직후 구독 전송 (별도 스레드에서 수행하여 Event Loop 비점유) */
    private void scheduleSubscription() {
        new Thread(() -> {
            try {
                Thread.sleep(500); // 핸드쉐이크 안정화 대기
                subscribeAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "kis-subscribe-thread").start();
    }

    // ──────────────────────────────────────────────────────────
    // 구독
    // ──────────────────────────────────────────────────────────

    public void setSubscriptionInfo(String approvalKey, List<String> tickers) {
        if (approvalKey != null) this.approvalKey = approvalKey;
        this.tickers = tickers;
        if (isChannelOpen()) subscribeAll();
    }

    private void subscribeAll() {
        if (tickers == null || tickers.isEmpty()) return;

        log.info("전체 종목 구독 시작 (총 {}종목)", tickers.size());
        for (String ticker : tickers) {
            if (!isChannelOpen()) {
                log.warn("채널이 닫혀 구독 중단 (ticker={})", ticker);
                break;
            }
            try {
                sendSubscribeMessage(ticker);
                Thread.sleep(100); // KIS 가이드 권장 간격
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("종목 구독 실패: {}", ticker, e);
            }
        }
        log.info("전체 종목 구독 완료");
        stockMetrics.setSubscribeCount(tickers != null ? tickers.size() : 0);
    }

    private void sendSubscribeMessage(String ticker) throws Exception {
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

        channel.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(request)));
    }

    // ──────────────────────────────────────────────────────────
    // 스케줄러
    // ──────────────────────────────────────────────────────────

    /** 장 마감 시 연결 종료 */
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void closeAtMarketClose() {
        log.info("장 마감 → KIS WebSocket 연결 종료");
        closeChannel();
        stockMetrics.setSubscribeCount(0);
    }

    /** 30초마다 연결 상태 확인 */
    @Scheduled(fixedDelay = 30_000)
    public void checkConnectionHealth() {
        if (!MarketTime.isMarketOpen()) return;

        if (!isChannelOpen()) {
            log.warn("WebSocket 연결 끊김 감지 (장 중). 재연결 시도...");
            if (!isReconnecting.get()) {
                scheduleReconnection();
            }
        } else {
            log.debug("WebSocket 연결 정상");
        }
    }

    // ──────────────────────────────────────────────────────────
    // 재연결
    // ──────────────────────────────────────────────────────────

    private void scheduleReconnection() {
        if (!isReconnecting.compareAndSet(false, true)) {
            log.info("재연결 이미 진행 중. 중복 실행 방지.");
            return;
        }

        new Thread(() -> {
            try {
                int retryCount = 0;
                while (!isChannelOpen() && MarketTime.isMarketOpen()) {
                    long delay = Math.min(5_000 + (long) retryCount * 5_000, 30_000);
                    log.info("{}초 후 재연결 시도... (시도: {}회)", delay / 1000, retryCount + 1);
                    Thread.sleep(delay);

                    try {
                        doConnect();
                        if (isChannelOpen()) {
                            log.info("재연결 성공");
                            break;
                        }
                    } catch (Exception e) {
                        log.error("재연결 시도 실패 ({}회): {}", retryCount + 1, e.getMessage());
                    }
                    retryCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isReconnecting.set(false);
                log.info("재연결 루프 종료 (isOpen={})", isChannelOpen());
            }
        }, "kis-reconnect-thread").start();
    }

    // ──────────────────────────────────────────────────────────
    // 유틸
    // ──────────────────────────────────────────────────────────

    public boolean isChannelOpen() {
        return channel != null && channel.isActive();
    }

    private void closeChannel() {
        if (isChannelOpen()) {
            channel.close().awaitUninterruptibly(3, TimeUnit.SECONDS);
        }
    }

    private SslContext buildSslContext(URI uri) throws Exception {
        if ("wss".equalsIgnoreCase(uri.getScheme())) {
            return SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE) // 개발 환경용 - 운영은 실제 인증서 사용
                    .build();
        }
        return null;
    }
}
