package com.project.demo.common.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.kis.AesDecryptUtil;
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
    private final StockMetrics stockMetrics;
    private final Executor broadcastExecutor;

    // 종목명 캐싱을 위한 메모리 저장소 (DB 부하 방지)
    private final Map<String, String> companyNameCache = new ConcurrentHashMap<>();

    public RedisStreamConsumer(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            StockRepository stockRepository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            @org.springframework.context.annotation.Lazy OrderService orderService,
            StockMetrics stockMetrics,
            @Qualifier("broadcastExecutor") Executor broadcastExecutor) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.stockRepository = stockRepository;
        this.messagingTemplate = messagingTemplate;
        this.orderService = orderService;
        this.stockMetrics = stockMetrics;
        this.broadcastExecutor = broadcastExecutor;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> value = message.getValue();
            if (value == null) return;
            
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
                    stockMetrics.recordProcessingTime(() -> processRawData(data));
                } else {
                    processRawData(data);
                }
            }
            // 4단계: 처리 완료 보고 (Acknowledgment)
            // 이를 통해 Consumer Group 내에서 메시지 중복 처리를 방지합니다.
            redisTemplate.opsForStream().acknowledge("stock:stream:realtime", "stock-group", message.getId());
        } catch (Throwable t) {
            log.error("스트림 메시지 처리 중 치명적 오류 발생 (Throwable): {}", t.getMessage(), t);
        }
    }

    private void processRawData(String data) {
        String[] fields = data.split("\\^");
        if (fields.length < 14) {
            log.warn("올바르지 않은 실시간 데이터 필드 개수 (fields < 14): {}", data);
            return;
        }

        try {
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

            // 1. Redis 최신가 및 랭킹 저장 (Pipelining 적용 - 네트워크 왕복 1회로 통합)
            try {
                redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    StringRedisConnection stringRedisConn = (StringRedisConnection) connection;
                    stringRedisConn.set("stock:data:" + ticker, json);
                    stringRedisConn.zAdd("stock:rank:volume", volume, ticker);
                    stringRedisConn.zAdd("stock:rank:price", price, ticker);
                    stringRedisConn.zAdd("stock:rank:changeRate", changeRate, ticker);
                    return null;
                });
                stockMetrics.recordRedisSave();
            } catch (Exception e) {
                log.error("Redis 랭킹 저장 중 오류 발생 - 종목: {}, 오류: {}", ticker, e.getMessage());
            }

            // 2. STOMP 브로드캐스팅 (비동기 처리 - 컨슈머 스레드 프리패스)
            CompletableFuture.runAsync(() -> {
                try {
                    messagingTemplate.convertAndSend("/topic/stocks", json);
                    stockMetrics.recordStompBroadcast();
                } catch (Exception e) {
                    log.error("STOMP 전송 중 비동기 오류 발생 - 종목: {}, 오류: {}", ticker, e.getMessage());
                }
            }, broadcastExecutor);

            // 요청하신 로그 문자열 추가
            // 로그 부하를 낮추기 위해 INFO -> DEBUG로 변경
            log.debug("실시간 주가 처리 완료: ticker={}, price={}", ticker, price);
            log.debug("Consumer 처리 상세 정보 → {}", json);

            // 3. 예약 주문 체결
            try {
                orderService.executeReservedOrdersForTicker(ticker, price);
            } catch (Exception e) {
                log.error("예약 주문 체결 중 오류 발생 - 종목: {}, 현재가: {}, 오류: {}", ticker, price, e.getMessage(), e);
            }

            // 최종 처리 완료 메트릭 기록 (Egress)
            if (stockMetrics != null) {
                stockMetrics.recordRealtimeProcessed();
            }

        } catch (Exception e) {
            log.error("실시간 데이터 파싱 중 상세 오류 발생 (data: {}): {}", data, e.getMessage());
        }
    }
}
