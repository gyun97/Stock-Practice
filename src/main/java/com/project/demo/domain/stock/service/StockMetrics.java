package com.project.demo.domain.stock.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 주식 도메인 커스텀 메트릭 (Prometheus 노출)
 *
 * <pre>
 * 노출 메트릭:
 *  - stock_subscribe_count          : 현재 KIS WebSocket 구독 중인 종목 수 (Gauge)
 *  - stock_kis_api_call_total       : KIS REST API 호출 누적 횟수 (Counter)
 *  - stock_kis_api_error_total      : KIS REST API 실패 누적 횟수 (Counter)
 *  - stock_stomp_broadcast_total    : STOMP 브로드캐스트 누적 횟수 (Counter)
 *  - stock_redis_save_total         : Redis 저장 누적 횟수 (Counter)
 * </pre>
 *
 * <p>사용법: StockMetrics 빈을 주입받아 각 이벤트 발생 시점에 메서드 호출</p>
 */
@Component
public class StockMetrics {

    // Gauge - 현재 구독 중인 종목 수 (가변)
    private final AtomicInteger subscribeCount = new AtomicInteger(0);

    // Counter - 누적 집계
    private final Counter kisApiCallCounter;
    private final Counter kisApiErrorCounter;
    private final Counter stompBroadcastCounter;
    private final Counter redisSaveCounter;

    public StockMetrics(MeterRegistry registry) {
        // Gauge: 현재 구독 종목 수
        Gauge.builder("stock.subscribe.count", subscribeCount, AtomicInteger::get)
                .description("현재 KIS WebSocket 구독 중인 종목 수")
                .tag("type", "websocket")
                .register(registry);

        // Counter: KIS REST API 호출 횟수
        this.kisApiCallCounter = Counter.builder("stock.kis.api.call")
                .description("KIS REST API 총 호출 횟수")
                .tag("type", "rest")
                .register(registry);

        // Counter: KIS REST API 실패 횟수
        this.kisApiErrorCounter = Counter.builder("stock.kis.api.error")
                .description("KIS REST API 실패 횟수")
                .tag("type", "rest")
                .register(registry);

        // Counter: STOMP 브로드캐스트 횟수
        this.stompBroadcastCounter = Counter.builder("stock.stomp.broadcast")
                .description("STOMP /topic/stocks 브로드캐스트 횟수")
                .tag("type", "stomp")
                .register(registry);

        // Counter: Redis 저장 횟수
        this.redisSaveCounter = Counter.builder("stock.redis.save")
                .description("Redis 주가 데이터 저장 횟수")
                .tag("type", "redis")
                .register(registry);
    }

    // ──────────────────────────────────────────
    // 구독 종목 수 조작
    // ──────────────────────────────────────────

    /** 구독 종목 수 설정 */
    public void setSubscribeCount(int count) {
        subscribeCount.set(count);
    }

    /** 구독 종목 수 증가 */
    public void incrementSubscribeCount() {
        subscribeCount.incrementAndGet();
    }

    /** 구독 종목 수 감소 */
    public void decrementSubscribeCount() {
        subscribeCount.decrementAndGet();
    }

    // ──────────────────────────────────────────
    // 카운터 증가 메서드
    // ──────────────────────────────────────────

    /** KIS REST API 호출 기록 */
    public void recordKisApiCall() {
        kisApiCallCounter.increment();
    }

    /** KIS REST API 오류 기록 */
    public void recordKisApiError() {
        kisApiErrorCounter.increment();
    }

    /** STOMP 브로드캐스트 기록 */
    public void recordStompBroadcast() {
        stompBroadcastCounter.increment();
    }

    /** Redis 저장 기록 */
    public void recordRedisSave() {
        redisSaveCounter.increment();
    }
}
