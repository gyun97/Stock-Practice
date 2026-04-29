package com.project.demo.domain.stock.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final Counter realtimeReceivedCounter; // 추가: 수신 전용
    private final Counter realtimeProcessedCounter; // 추가: 처리 전용
    
    // Timer - 처리 지연 시간 측정용
    private final Timer processingTimer;

    // Gauge - Redis Lag
    private double redisLag = 0;

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

        // Counter: Redis 저장 횟수 (기존 유지)
        this.redisSaveCounter = Counter.builder("stock.redis.save")
                .description("Redis 주가 데이터 저장 횟수")
                .tag("type", "redis")
                .register(registry);

        // Counter: 수신(Ingress) 건수
        this.realtimeReceivedCounter = Counter.builder("stock.realtime.received")
                .description("Netty로부터 수신된 실시간 주가 건수")
                .register(registry);

        // Counter: 처리(Egress) 건수
        this.realtimeProcessedCounter = Counter.builder("stock.realtime.processed")
                .description("Consumer가 처리를 완료한 실시간 주가 건수")
                .register(registry);
                
        // Timer: 주가 데이터 처리 지연 시간
        this.processingTimer = Timer.builder("stock.processing.time")
                .description("WebSocket 주가 수신부터 처리 완료까지 걸린 시간")
                .publishPercentileHistogram() // 히스토그램 추가 (P95, P99 등 집계용)
                .register(registry);

        // Gauge: Redis Stream Lag 등록
        Gauge.builder("stock.realtime.redis.lag", this, metrics -> metrics.redisLag)
                .description("Redis Stream Lag (미처리 메시지 수)")
                .register(registry);
    }

    // ──────────────────────────────────────────
    // 구독 종목 수 조작
    // ──────────────────────────────────────────

    /** Redis Stream Lag 수치 업데이트 */
    public void updateRedisLag(double lag) {
        this.redisLag = lag;
    }

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

    /** 실시간 주가 수신 기록 (Ingress) */
    public void recordRealtimeReceived() {
        realtimeReceivedCounter.increment();
    }

    /** 실시간 주가 처리 완료 기록 (Egress) */
    public void recordRealtimeProcessed() {
        realtimeProcessedCounter.increment();
    }
    
    /** 처리 지연 시간 기록 */
    public void recordProcessingTime(Runnable runnable) {
        processingTimer.record(runnable);
    }

    /** 실시간 수신 누적 건수 조회 (로깅용) */
    public long getRealtimeReceivedCount() {
        return (long) realtimeReceivedCounter.count();
    }

    /** 실시간 처리 누적 건수 조회 (로깅용) */
    public long getRealtimeProcessedCount() {
        return (long) realtimeProcessedCounter.count();
    }
}
