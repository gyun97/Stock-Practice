package com.project.demo.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.kis.AesDecryptUtil;
import com.project.demo.domain.execution.service.ExecutionService;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.stock.service.StockMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Streams Consumer.
 *
 * <pre>
 * 역할:
 *  - Netty가 적재한 실시간 주가 데이터(stock:stream:realtime)를 비동기로 꺼내어 처리
 *  - 데이터 파싱, Redis 저장(ZSet 랭킹 등), STOMP 브로드캐스팅, 예약 주문 체결
 *  - 이 로직이 지연되더라도 Netty의 Event Loop(수신)는 전혀 영향을 받지 않습니다.
 * </pre>
 */
@Slf4j
@Component
public class RedisStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final StockRepository stockRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OrderService orderService;
    private final ExecutionService executionService;
    private final StockMetrics stockMetrics;
    private final Executor broadcastExecutor;
    private final Executor orderExecutor;

    // 종목명 캐싱을 위한 메모리 저장소 (DB 부하 방지)
    private final Map<String, String> companyNameCache = new ConcurrentHashMap<>();

    public RedisStreamConsumer(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            StockRepository stockRepository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            @org.springframework.context.annotation.Lazy OrderService orderService,
            ExecutionService executionService,
            StockMetrics stockMetrics,
            @Qualifier("broadcastExecutor") Executor broadcastExecutor,
            @Qualifier("orderExecutor") Executor orderExecutor) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.stockRepository = stockRepository;
        this.messagingTemplate = messagingTemplate;
        this.orderService = orderService;
        this.executionService = executionService;
        this.stockMetrics = stockMetrics;
        this.broadcastExecutor = broadcastExecutor;
        this.orderExecutor = orderExecutor;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String streamKey = message.getStream();
        try {
            Map<String, String> value = message.getValue();
            if (value == null) {
                redisTemplate.opsForStream().acknowledge(streamKey, "stock-group", message.getId());
                return;
            }

            String type = value.get("type");

            if ("ENC".equals(type)) {
                String encryptedData = value.get("data");
                String iv = value.get("iv");
                String key = value.get("key");

                String decrypted = AesDecryptUtil.decrypt(encryptedData, key, iv);
                log.info("복호화 결과 (Consumer): {}", decrypted);
            } else if ("RAW".equals(type)) {
                String data = value.get("data");
                if (stockMetrics != null) {
                    stockMetrics.recordProcessingTime(() -> {
                        try {
                            processRawData(data);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    processRawData(data);
                }
            }
            // 정상 처리 완료 보고 (Acknowledgment)
            redisTemplate.opsForStream().acknowledge(streamKey, "stock-group", message.getId());
        } catch (Throwable t) {
            log.error("스트림 메시지 처리 중 오류 발생 - DLQ 이관 시도: {}", t.getMessage(), t);
            moveToDlq(message, t);
        }
    }

    /**
     * 실패한 메시지를 Dead Letter Queue(DLQ)로 이관하고 원본 메시지를 Acknowledge 처리합니다.
     */
    private void moveToDlq(MapRecord<String, String, String> message, Throwable t) {
        try {
            Map<String, String> originalValue = message.getValue();
            java.util.Map<String, String> dlqValue = new java.util.HashMap<>(originalValue);
            dlqValue.put("error_message", t.getMessage());
            dlqValue.put("error_class", t.getClass().getName());
            dlqValue.put("failed_at", java.time.LocalDateTime.now().toString());

            MapRecord<String, String, String> dlqRecord = MapRecord.create(
                    RedisStreamProducer.DLQ_STREAM_KEY,
                    dlqValue);

            redisTemplate.opsForStream().add(dlqRecord);
            log.info("메시지 DLQ 이관 완료: MessageId={}, DLQ_Key={}", message.getId(), RedisStreamProducer.DLQ_STREAM_KEY);

            // DLQ 이관이 성공했으므로 원본 메시지 Ack 처리 (PEL 누수 방지)
            redisTemplate.opsForStream().acknowledge(message.getStream(), "stock-group", message.getId());
        } catch (Exception e) {
            log.error("DLQ 이관 중 추가 오류 발생 (치명적): {}", e.getMessage(), e);
        }
    }

    private void processRawData(String data) throws Exception {
        String[] fields = data.split("\\^");
        if (fields.length < 14) {
            log.warn("올바르지 않은 실시간 데이터 필드 개수 (fields < 14): {}", data);
            return;
        }

        String ticker = fields[0];
        String tradeTime = fields[1];
        int price = Integer.parseInt(fields[2]);
        double changeAmount = Double.parseDouble(fields[4]);
        double changeRate = Double.parseDouble(fields[5]);
        long volume = Long.parseLong(fields[13]);

        // 캐시에서 종목명 조회, 없으면 DB 조회 후 캐시 저장
        String companyName = companyNameCache.computeIfAbsent(ticker, k -> {
            log.debug("캐시 미스 - DB에서 종목명 조회: {}", k);
            return stockRepository.findNameByTicker(k);
        });

        ObjectNode out = objectMapper.createObjectNode();
        out.put("ticker", ticker);
        out.put("price", price);
        out.put("changeAmount", changeAmount);
        out.put("changeRate", changeRate);
        out.put("companyName", companyName);
        out.put("tradeTime", tradeTime);
        out.put("volume", volume);

        String json = objectMapper.writeValueAsString(out);

        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            StringRedisConnection stringRedisConn = (StringRedisConnection) connection;
            stringRedisConn.set("stock:data:" + ticker, json);
            stringRedisConn.zAdd("stock:rank:volume", (double) volume, ticker);
            stringRedisConn.zAdd("stock:rank:price", (double) price, ticker);
            stringRedisConn.zAdd("stock:rank:changeRate", changeRate, ticker);
            return null;
        });
        if (stockMetrics != null) {
            stockMetrics.recordRedisSave();
        }

        // 2. STOMP 브로드캐스팅 (비동기 처리)
        CompletableFuture.runAsync(() -> {
            try {
                messagingTemplate.convertAndSend("/topic/stocks", json);
                if (stockMetrics != null) {
                    stockMetrics.recordStompBroadcast();
                }
            } catch (Exception e) {
                log.error("STOMP 전송 중 비동기 오류 발생 - 종목: {}, 오류: {}", ticker, e.getMessage());
            }
        }, broadcastExecutor);

        log.debug("실시간 주가 처리 완료: ticker={}, price={}", ticker, price);

        // 3. 예약 주문 체결 (비동기 처리)
        CompletableFuture.runAsync(() -> {
            try {
                executionService.executeReservedOrdersForTicker(ticker, price);
            } catch (Exception e) {
                log.error("예약 주문 체결 중 비동기 오류 발생 - 종목: {}, 오류: {}", ticker, e.getMessage());
            }
        }, orderExecutor);

        // 최종 처리 완료 메트릭 기록
        if (stockMetrics != null) {
            stockMetrics.recordRealtimeProcessed();
        }
    }

}
