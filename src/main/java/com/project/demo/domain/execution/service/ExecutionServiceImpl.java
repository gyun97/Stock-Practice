package com.project.demo.domain.execution.service;

import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.domain.execution.entity.Execution;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.project.demo.common.websocket.WebSocketSessionManager;
import com.project.demo.domain.order.dto.response.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionServiceImpl implements ExecutionService {

    private final ExecutionRepository executionRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserStockRepository userStockRepository;
    private final OrderRepository orderRepository;
    private final WebSocketSessionManager sessionManager;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    // --- 즉시 주문용 (Propagation.REQUIRED) ---

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        processBuy(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        processSell(orderId, user, stock, price, quantity, totalPrice);
    }

    // --- 예약 주문용 (Propagation.REQUIRES_NEW) ---

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeReservedBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        processBuy(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeReservedSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        processSell(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    @Transactional
    public void executeReservedOrdersForTicker(String ticker, int currentPrice) {
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
                    // 예약 매수: 독립 트랜잭션 보장
                    executeReservedBuy(order.getId(), order.getUser(), order.getStock(),
                            currentPrice,
                            order.getQuantity(),
                            (long) currentPrice * order.getQuantity());

                    processedCount++;
                    sendExecutionNotification(order);
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
                    // 예약 매도: 독립 트랜잭션 보장
                    executeReservedSell(order.getId(), order.getUser(), order.getStock(),
                            currentPrice,
                            order.getQuantity(),
                            (long) currentPrice * order.getQuantity());

                    processedCount++;
                    sendExecutionNotification(order);
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

    // --- 내부 공통 체결 로직 ---

    private void processBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        // 1. 주문 비관적 락 조회
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다. ID: " + orderId));

        if (order.isExecuted()) {
            return;
        }

        // 2. 포트폴리오 비관적 락 획득
        Portfolio portfolio = portfolioRepository.findWithLockByUserId(user.getId())
                .orElseThrow(NotFoundPortfolioException::new);
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        if (portfolio.getBalance() < totalPrice) {
            throw new NotEnoughMoneyException();
        }

        // 3. 체결 및 보유 주식 업데이트
        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.BUY)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();
        executionRepository.save(execution);

        UserStock userStock = getOrCreateUserStock(user, stock, portfolio, price, quantity);
        userStock.increasePurchaseAmount((long) price * quantity);
        portfolio.addUserStock(userStock);
        updatePortfolioAfterBuy(portfolio, price, quantity);

        order.markExecuted();
        entityManager.flush();
    }

    private void processSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        // 1. 주문 비관적 락 조회
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다. ID: " + orderId));

        if (order.isExecuted()) {
            return;
        }

        // 2. 체결 생성
        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.SELL)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();
        executionRepository.save(execution);

        // 3. 포트폴리오 및 보유주식 락 획득
        Portfolio portfolio = portfolioRepository.findWithLockByUserId(user.getId())
                .orElseThrow(NotFoundPortfolioException::new);
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        UserStock userStock = userStockRepository.findByUserAndStockWithLock(user.getId(), stock.getId())
                .orElseThrow(NotEnoughStockException::new);
        entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);

        if (userStock.getTotalQuantity() < quantity) {
            throw new NotEnoughStockException();
        }

        updateUserStockAndPortfolioAfterSell(user, stock, portfolio, userStock, price, quantity);

        order.markExecuted();
        entityManager.flush();
    }

    private UserStock getOrCreateUserStock(User user, Stock stock, Portfolio portfolio, int price, int quantity) {
        try {
            Optional<UserStock> optionalUserStock = userStockRepository.findByUserAndStockWithLock(user.getId(),
                    stock.getId());
            if (optionalUserStock.isPresent()) {
                UserStock userStock = optionalUserStock.get();
                entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);
                userStock.updateAfterBuy(price, quantity);
                return userStock;
            } else {
                UserStock userStock = UserStock.builder()
                        .user(user).stock(stock).avgPrice(price).totalQuantity(quantity)
                        .ticker(stock.getTicker()).portfolio(portfolio)
                        .userName(user.getName()).stockName(stock.getName())
                        .build();
                return userStockRepository.saveAndFlush(userStock);
            }
        } catch (DataIntegrityViolationException e) {
            UserStock userStock = userStockRepository.findByUserAndStockWithLock(user.getId(), stock.getId())
                    .orElseThrow(() -> new RuntimeException("UserStock 조회 실패"));
            entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);
            userStock.updateAfterBuy(price, quantity);
            return userStock;
        }
    }

    private void updateUserStockAndPortfolioAfterSell(User user, Stock stock, Portfolio portfolio, UserStock userStock,
            int price, int quantity) {
        int remainingQuantity = userStock.getTotalQuantity() - quantity;
        long avgPrice = userStock.getAvgPrice();
        long profit = (long) (price - avgPrice) * quantity;

        log.info("[매도체결] {} 매도 수익: {}원", stock.getName(), profit);

        if (remainingQuantity > 0) {
            userStock.updateQuantity(remainingQuantity);
            userStock.decreasePurchaseAmount(quantity);
        } else {
            userStockRepository.delete(userStock);
            user.getUserStocks().remove(userStock);
            stock.getUserStocks().remove(userStock);
            portfolio.getUserStocks().remove(userStock);
        }
        updatePortfolioAfterSell(portfolio, price, quantity);
    }

    private void updatePortfolioAfterBuy(Portfolio portfolio, int buyPrice, int quantity) {
        long increaseStockValue = (long) buyPrice * quantity;
        portfolio.increaseStockAsset(increaseStockValue);
        portfolio.decreaseBalance(increaseStockValue);
        portfolio.increaseTotalQuantity(quantity);
        portfolio.recalculateTotalAsset();
        portfolio.updateHoldCount();
    }

    private void updatePortfolioAfterSell(Portfolio portfolio, int sellPrice, int quantity) {
        long decreasedStockValue = (long) sellPrice * quantity;
        portfolio.decreaseStockAsset(decreasedStockValue);
        portfolio.increaseBalance(decreasedStockValue);
        portfolio.decreaseTotalQuantity(quantity);
        portfolio.recalculateTotalAsset();
        portfolio.updateHoldCount();
    }
}
