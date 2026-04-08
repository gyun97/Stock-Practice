package com.project.demo.common.redis;

import com.project.demo.domain.stock.service.StockMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
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
@RequiredArgsConstructor
public class RedisStreamProducer {

    private final StringRedisTemplate redisTemplate;
    private final StockMetrics stockMetrics;

    public static final String STREAM_KEY = "stock:stream:realtime";

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

            redisTemplate.opsForStream().add(record);
            
            // 메트릭 기록 (수신 성공 카운트 - Ingress)
            if (stockMetrics != null) {
                stockMetrics.recordRealtimeReceived(); 
            }

            // 로그 부하 및 Grafana Cloud 용량 관리를 위해 DEBUG로 하향
            log.debug("실시간 주가 수신 완료 (Redis Stream 적재): ticker={}", ticker);
            log.debug("Redis Streams XADD 완료: ticker={}", ticker);

        } catch (Exception e) {
            // Producer 실패 시 로그만 남기고 수신부로 예외를 전파하지 않음
            // → Netty Event Loop를 절대 중단시키지 않는 것이 최우선
            log.error("Redis Streams XADD 실패 (ticker={}): {}", ticker, e.getMessage());
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

            redisTemplate.opsForStream().add(record);
            log.debug("Redis Streams XADD (암호화) 완료");

        } catch (Exception e) {
            log.error("Redis Streams XADD (암호화) 실패: {}", e.getMessage());
        }
    }
}
