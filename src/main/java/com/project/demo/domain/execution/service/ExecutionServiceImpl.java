package com.project.demo.domain.execution.service;

import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

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

    // --- 즉시 주문용 (별도 컴포넌트로 위임) ---

    @Override
    public void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        executionProcessService.executeBuy(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    public void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        executionProcessService.executeSell(orderId, user, stock, price, quantity, totalPrice);
    }

    // --- 예약 주문용 (별도 컴포넌트로 위임하여 REQUIRES_NEW 보장) ---

    @Override
    public void executeReservedOrdersForTicker(String ticker, int currentPrice) {
        // [1단계] 전체 작업을 감싸는 최상위 부모 스팬 생성
        Observation totalObservation = Observation.createNotStarted("reserved.orders.execution", observationRegistry)
                .contextualName("reserved.orders.execution.span")
                .lowCardinalityKeyValue("ticker", ticker);

        try (Observation.Scope totalScope = totalObservation.start().openScope()) {
            log.info("[예약주문체결-전체시작] 종목: {}, 현재가: {} | TraceId: {}", ticker, currentPrice, org.slf4j.MDC.get("traceId"));

            long lookupStartTime = System.currentTimeMillis();
            List<Long> executableOrderIds = orderRepository.findExecutableReservedOrderIds(ticker, currentPrice);
            long lookupElapsedMs = System.currentTimeMillis() - lookupStartTime;

            if (executableOrderIds.isEmpty()) return;

            // 조회 시간 별도로 기록
            Timer.builder("reserved.order.lookup.time")
                    .tag("ticker", ticker)
                    .tag("engine", "db")
                    .description("예약 주문 대상 조회(ID list) 소요 시간")
                    .register(meterRegistry)
                    .record(Duration.ofMillis(lookupElapsedMs));

            log.info("[예약주문조회-완료] 종목: {}, 조회된 주문수: {}, 소요시간: {}ms", ticker, executableOrderIds.size(), lookupElapsedMs);

            long startTime = System.currentTimeMillis();
            int totalProcessedCount = 0;
            int batchSize = 100;

            for (int i = 0; i < executableOrderIds.size(); i += batchSize) {
                List<Long> chunk = executableOrderIds.subList(i, Math.min(i + batchSize, executableOrderIds.size()));
                
                // [2단계] 100건 단위의 배치 부모 스팬 생성
                Observation batchObservation = Observation.createNotStarted("reserved.orders.batch", observationRegistry)
                        .contextualName("reserved.orders.batch.span")
                        .lowCardinalityKeyValue("ticker", ticker)
                        .lowCardinalityKeyValue("chunkIndex", String.valueOf(i / batchSize));

                try (Observation.Scope batchScope = batchObservation.start().openScope()) {
                    for (Long orderId : chunk) {
                        try {
                            // [3단계] 개별 주문 체결 (@Observed에 의해 자식으로 생성됨)
                            executionProcessService.executeReservedOrder(orderId, currentPrice);
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
            entityManager.refresh(order); // 최신 상태 반영 (isExecuted = true 등)
            sessionManager.sendOrderNotification(order.getUser().getId(),
                    com.project.demo.domain.order.dto.response.OrderResponse.of(order));
        } catch (Exception e) {
            log.error("주문 알림 전송 중 오류 발생 - 주문 ID: {}, 오류: {}", order.getId(), e.getMessage());
        }
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
