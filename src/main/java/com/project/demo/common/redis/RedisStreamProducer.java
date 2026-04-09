package com.project.demo.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis Streams Producer.
 *
 * <pre>
 * KIS WebSocket 수신부(Netty)가 메시지를 받자마자 즉시 호출합니다.
 * 이 클래스는 오직 XADD만 수행하며, 어떠한 비즈니스 로직도 포함하지 않습니다.
 *
 * Stream 키 : stock:stream:realtime
 * 필드 구조:
 *   - type    : "RAW" (평문) | "ENC" (AES 암호화)
 *   - ticker  : 종목코드 (평문 데이터인 경우만)
 *   - data    : 원본 파이프(|) 구분 데이터 문자열
 *   - iv      : AES IV (암호화 데이터인 경우만)
 *   - key     : AES KEY (암호화 데이터인 경우만)
 * </pre>
 */
@Slf4j
@Component
public class RedisStreamProducer {

    private final StringRedisTemplate redisTemplate;
    private final ThreadPoolTaskExecutor producerTaskExecutor;

    public RedisStreamProducer(
            StringRedisTemplate redisTemplate,
            @Qualifier("producerTaskExecutor") ThreadPoolTaskExecutor producerTaskExecutor) {
        this.redisTemplate = redisTemplate;
        this.producerTaskExecutor = producerTaskExecutor;
    }

    public static final String STREAM_KEY = "stock:stream:realtime";
    public static final String DLQ_STREAM_KEY = "stock:stream:realtime:dlq";

    /**
     * 평문 실시간 주가 데이터를 스트림에 적재합니다.
     *
     * @param ticker  종목코드 (예: "005930")
     * @param rawData 파이프 구분 원본 데이터 (예: "005930^093000^75000^...")
     */
    public void publish(String ticker, String rawData) {
        try {
            MapRecord<String, String, String> record = StreamRecords.string(
                    Map.of(
                            "type",   "RAW",
                            "ticker", ticker,
                            "data",   rawData
                    )
            ).withStreamKey(STREAM_KEY);

            CompletableFuture.runAsync(() -> {
                try {
                    redisTemplate.opsForStream().add(record);
                    log.debug("실시간 주가 수신 완료 (Redis Stream 적재): ticker={}", ticker);
                } catch (Exception e) {
                    log.error("Redis Streams XADD 비동기 실패 (ticker={}): {}", ticker, e.getMessage());
                }
            }, producerTaskExecutor);

        } catch (Exception e) {
            log.error("Redis Streams 레코드 생성 실패 (ticker={}): {}", ticker, e.getMessage());
        }
    }

    /**
     * AES 암호화된 실시간 데이터를 스트림에 적재합니다.
     * iv/key를 함께 저장하여 Consumer에서 복호화합니다.
     *
     * @param encryptedData Base64 인코딩된 암호문
     * @param iv            AES CBC IV
     * @param key           AES 키
     */
    public void publishEncrypted(String encryptedData, String iv, String key) {
        try {
            MapRecord<String, String, String> record = StreamRecords.string(
                    Map.of(
                            "type", "ENC",
                            "data", encryptedData,
                            "iv",   iv,
                            "key",  key
                    )
            ).withStreamKey(STREAM_KEY);

            CompletableFuture.runAsync(() -> {
                try {
                    redisTemplate.opsForStream().add(record);
                    log.debug("Redis Streams XADD (암호화) 완료");
                } catch (Exception e) {
                    log.error("Redis Streams XADD (암호화) 비동기 실패: {}", e.getMessage());
                }
            }, producerTaskExecutor);

        } catch (Exception e) {
            log.error("Redis Streams (암호화) 레코드 생성 실패: {}", e.getMessage());
        }
    }
}
