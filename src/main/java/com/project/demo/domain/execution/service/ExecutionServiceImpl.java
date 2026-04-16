package com.project.demo.domain.execution.service;

import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.util.MarketTime;
import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import com.project.demo.common.websocket.WebSocketSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionServiceImpl implements ExecutionService {

    private final OrderRepository orderRepository;
    private final WebSocketSessionManager sessionManager;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final ExecutionProcessService executionProcessService;
    private final ObservationRegistry observationRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    private final StringRedisTemplate redisTemplate;

    @Value("${app.reservation.engine:db}")
    private String reservationEngine;

    // --- 즉시 주문용 (별도 컴포넌트로 위임) ---

    @Override
    public void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        executionProcessService.processBuy(order, price, totalPrice);
    }

    @Override
    public void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다."));
        executionProcessService.processSell(order, price, totalPrice);
    }

    // --- 예약 주문용 (별도 컴포넌트로 위임하여 REQUIRES_NEW 보장) ---

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    @Observed(name = "reserved.orders.execution")
    public void executeReservedOrdersForTicker(String ticker, int currentPrice) {
        boolean marketOpen = MarketTime.isMarketOpen();

        if (!marketOpen) return;

        // [1단계] 전체 작업을 감싸는 최상위 부모 스팬 생성
        Observation totalObservation = Observation.createNotStarted("reserved.orders.execution", observationRegistry)
                .contextualName("reserved.orders.execution.span")
                .lowCardinalityKeyValue("ticker", ticker);

        try (Observation.Scope totalScope = totalObservation.start().openScope()) {

            long lookupStartNano = System.nanoTime();
            List<Long> executableOrderIds;

            if ("redis".equalsIgnoreCase(reservationEngine)) {
                executableOrderIds = findExecutableOrderIdsFromRedis(ticker, currentPrice);
            } else {
                executableOrderIds = orderRepository.findExecutableReservedOrderIds(ticker, currentPrice);
            }

            if (executableOrderIds != null && !executableOrderIds.isEmpty()) {
                log.info("[예약주문발견] 종목: {}, 발견된 주문수: {}, 현재가: {}", ticker, executableOrderIds.size(), currentPrice);
            }

            if (executableOrderIds.isEmpty())
                return;

            long lookupElapsedNano = System.nanoTime() - lookupStartNano;

            // 성능 측정: 실제 체결 대상이 발견된 경우만 기록하여 병목을 극적으로 시각화
            Timer.builder("reserved.order.lookup.time")
                    .tag("engine", reservationEngine.toLowerCase())
                    .description("예약 주문 대상 조회(ID list) 소요 시간")
                    .register(meterRegistry)
                    .record(Duration.ofNanos(lookupElapsedNano));

            long startTime = System.currentTimeMillis();
            int totalProcessedCount = 0;
            int batchSize = 100;

            for (int i = 0; i < executableOrderIds.size(); i += batchSize) {
                List<Long> chunk = executableOrderIds.subList(i, Math.min(i + batchSize, executableOrderIds.size()));

                // [2단계] 100건 단위의 배치 부모 스팬 생성
                Observation batchObservation = Observation
                        .createNotStarted("reserved.orders.batch", observationRegistry)
                        .contextualName("reserved.orders.batch.span")
                        .lowCardinalityKeyValue("ticker", ticker)
                        .lowCardinalityKeyValue("chunkIndex", String.valueOf(i / batchSize));

                try (Observation.Scope batchScope = batchObservation.start().openScope()) {
                    for (Long orderId : chunk) {
                        try {
                            // [3단계] 개별 주문 체결 (@Observed에 의해 자식으로 생성됨)
                            Order order = executionProcessService
                                    .executeReservedOrder(orderId, currentPrice);

                            log.info("[체결결과확인] 주문ID: {}, 체결여부(객체상태): {}", orderId,
                                    order != null ? order.isExecuted() : "null");

                            // 예약 주문 체결 성공 시에만 알림 전송
                            if (order != null && order.isExecuted()) {
                                sendExecutionNotification(order);
                            }

                            totalProcessedCount++;
                            recordMetric(ticker, "RESERVED", "success");
                        } catch (NotEnoughMoneyException | NotEnoughStockException e) {
                            log.warn("[예약주문체결실패] 주문 ID: {}, 사유: {}", orderId, e.getMessage());
                            recordMetric(ticker, "RESERVED", "fail_validation");
                        } catch (Exception e) {
                            batchObservation.error(e);
                            log.error("[예약주문체결오류] 주문 ID: {}, 오류: {}", orderId, e.getMessage());
                            recordMetric(ticker, "RESERVED", "error");
                        }
                    }
                } finally {
                    batchObservation.stop();
                }
            }

            if (totalProcessedCount > 0) {
                long elapsedMs = System.currentTimeMillis() - startTime;
                Timer.builder("reserved.order.execution.time")
                        .tag("ticker", ticker)
                        .description("예약 주문 체결 전체 소요 시간")
                        .register(meterRegistry)
                        .record(Duration.ofMillis(elapsedMs));
                log.info("[예약주문체결-전체종료] 종목: {}, 총 처리건수: {}, 총 소요시간: {}ms", ticker, totalProcessedCount, elapsedMs);
            }
        } finally {
            totalObservation.stop();
        }
    }

    private void sendExecutionNotification(Order order) {
        try {
            // 개별 트랜잭션에서 이미 업데이트된 상태를 반환받았으므로 refresh 생략 (Entity not managed 방지)

            String side = order.getType() == OrderType.BUY ? "매수" : "매도";
            String msg = String.format("[%s] %s %d주 %s 체결 완료",
                    order.isReserved() ? "예약" : "일반",
                    order.getStock().getName(),
                    order.getQuantity(),
                    side);

            log.info("[알림전송시도] 유저: {}, 메시지: {}", order.getUser().getId(), msg);

            sessionManager.sendOrderNotification(order.getUser().getId(),
                    OrderResponse.of(order, msg));
        } catch (Exception e) {
            log.error("주문 알림 전송 중 오류 발생 - 주문 ID: {}, 오류: {}", order.getId(), e.getMessage());
        }
    }

    private List<Long> findExecutableOrderIdsFromRedis(String ticker, int currentPrice) {
        String buyKey = "order:reserved:buy:" + ticker;
        String sellKey = "order:reserved:sell:" + ticker;

        // BUY: 현재가 <= 예약가 (예약가 >= 현재가) -> [currentPrice, +inf]
        Set<String> buyOrderIds = redisTemplate.opsForZSet().rangeByScore(buyKey, currentPrice,
                Double.MAX_VALUE);

        // SELL: 현재가 >= 예약가 (예약가 <= 현재가) -> [-inf, currentPrice]
        Set<String> sellOrderIds = redisTemplate.opsForZSet().rangeByScore(sellKey, 0, currentPrice);

        List<Long> result = new ArrayList<>();
        if (buyOrderIds != null)
            buyOrderIds.forEach(id -> result.add(Long.valueOf(id)));
        if (sellOrderIds != null)
            sellOrderIds.forEach(id -> result.add(Long.valueOf(id)));

        return result;
    }

    private void recordMetric(String ticker, String type, String result) {
        Counter.builder("reserved.order.execution.count")
                .tag("ticker", ticker)
                .tag("type", type)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
