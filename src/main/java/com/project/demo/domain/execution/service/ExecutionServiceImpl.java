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
    public void executeReservedBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        executionProcessService.executeReservedBuy(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    public void executeReservedSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        executionProcessService.executeReservedSell(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    public void executeReservedOrdersForTicker(String ticker, int currentPrice) {
        log.info("[예약주문체결-시작] 종목: {}, 현재가: {}", ticker, currentPrice);
        List<Order> reservedOrders = orderRepository.findReservedOrdersByTicker(ticker);

        if (reservedOrders.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int processedCount = 0;

        for (Order order : reservedOrders) {
            // 예약 매수 조건: 현재가 <= 예약가
            if (order.getType() == OrderType.BUY && currentPrice <= order.getPrice()) {
                try {
                    // 예약 매수: 외부 서비스 호출을 통해 독립 트랜잭션(REQUIRES_NEW) 보장
                    executionProcessService.executeReservedBuy(order.getId(), order.getUser(), order.getStock(),
                            currentPrice,
                            order.getQuantity(),
                            (long) currentPrice * order.getQuantity());

                    processedCount++;
                    // sendExecutionNotification(order);
                    recordMetric(ticker, "BUY", "success");
                } catch (NotEnoughMoneyException e) {
                    log.warn("[예약매수체결실패] 주문 ID: {}, 사유: 잔액 부족", order.getId());
                    recordMetric(ticker, "BUY", "fail_no_money");
                } catch (Exception e) {
                    log.error("[예약매수체결오류] 주문 ID: {}, 오류: {}", order.getId(), e.getMessage());
                    recordMetric(ticker, "BUY", "error");
                }
            }

            // 예약 매도 조건: 현재가 >= 예약가
            if (order.getType() == OrderType.SELL && currentPrice >= order.getPrice()) {
                try {
                    // 예약 매도: 외부 서비스 호출을 통해 독립 트랜잭션(REQUIRES_NEW) 보장
                    executionProcessService.executeReservedSell(order.getId(), order.getUser(), order.getStock(),
                            currentPrice,
                            order.getQuantity(),
                            (long) currentPrice * order.getQuantity());

                    processedCount++;
                    // sendExecutionNotification(order);
                    recordMetric(ticker, "SELL", "success");
                } catch (NotEnoughStockException e) {
                    log.warn("[예약매도체결실패] 주문 ID: {}, 사유: 주식 부족", order.getId());
                    recordMetric(ticker, "SELL", "fail_no_stock");
                } catch (Exception e) {
                    log.error("[예약매도체결오류] 주문 ID: {}, 오류: {}", order.getId(), e.getMessage());
                    recordMetric(ticker, "SELL", "error");
                }
            }
        }

        if (processedCount > 0) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            Timer.builder("reserved.order.execution.time")
                    .tag("ticker", ticker)
                    .description("예약 주문 체결 배치 소요 시간")
                    .register(meterRegistry)
                    .record(Duration.ofMillis(elapsedMs));
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
