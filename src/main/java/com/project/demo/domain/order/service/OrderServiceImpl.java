package com.project.demo.domain.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.order.ExecutedOrderException;
import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.order.NotFoundOrderException;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.common.exception.stock.NotFoundStockException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.domain.execution.entity.Execution;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.dto.response.StockData;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import com.project.demo.common.websocket.WebSocketSessionManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

        private final OrderRepository orderRepository;
        private final UserRepository userRepository;
        private final UserStockRepository userStockRepository;
        private final StockRepository stockRepository;
        private final ExecutionRepository executionRepository;
        private final PortfolioRepository portfolioRepository;
        private final ObjectMapper objectMapper;
        private final StringRedisTemplate redisTemplate;
        private final WebSocketSessionManager sessionManager;

        @PersistenceContext
        private EntityManager entityManager;

        /*
         * 주식 즉시 매수
         */
        @Transactional
        public String buyingStock(Long userId, String ticker, int quantity) {

                // 유저
                User user = userRepository.findById(userId)
                                .orElseThrow(NotFoundUserException::new);

                Portfolio portfolio = portfolioRepository.findWithLockByUser(user)
                                .orElseThrow(NotFoundPortfolioException::new);

                Stock stock = stockRepository.findByTicker(ticker)
                                .orElseThrow(NotFoundStockException::new);

                // 주식 현재가
                int stockPrice = getStockPrice(ticker);

                // 총 주문 가격
                long totalPrice = (long) stockPrice * quantity;

                // 보유금이 모자르다면
                if (portfolio.getBalance() < totalPrice) {
                        throw new NotEnoughMoneyException();
                }

                Order newOrder = Order.builder()
                                .type(OrderType.BUY)
                                .price(stockPrice)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .user(user)
                                .stock(stock)
                                .type(OrderType.BUY)
                                .isExecuted(true)
                                .isReserved(false)
                                .build();

                orderRepository.save(newOrder);

                stock.getOrders().add(newOrder);

                // 주문 체결
                executeBuy(newOrder, user, stock, stockPrice, quantity, totalPrice);

                return stock.getName() + " 주식 " + newOrder.getQuantity() + "주 구매에 성공하였습니다!";
        }

        /*
         * 매수 주문 체결
         */
        @Transactional
        public void executeBuy(Order order, User user, Stock stock, int price, int quantity, long totalPrice) {
                // 1. 포트폴리오 비관적 락 획득 (ID 기반으로 세션 내 일관성 보장)
                Portfolio portfolio = portfolioRepository.findWithLockByUserId(user.getId())
                                .orElseThrow(NotFoundPortfolioException::new);

                // 2. MySQL REPEATABLE READ 격리 수준에서 스냅샷을 우회하고 최신 데이터를 강제 로드하기 위해 락 모드와 함께 refresh
                entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

                // 잔고 확인
                if (portfolio.getBalance() < totalPrice) {
                        throw new NotEnoughMoneyException();
                }

                // 3. 체결 생성
                Execution execution = Execution.builder()
                                .order(order)
                                .type(OrderType.BUY)
                                .price(price)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .build();

                executionRepository.save(execution);

                // 4. 보유 주식(UserStock) 비관적 락 조회
                UserStock userStock;
                try {
                        Optional<UserStock> optionalUserStock = userStockRepository.findByUserAndStockWithLock(user.getId(),
                                        stock.getId());

                        if (optionalUserStock.isPresent()) {
                                userStock = optionalUserStock.get();
                                // unblock된 후 최신 상태 반영을 위해 락 모드와 함께 refresh (snapshot 우회)
                                entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);
                                userStock.updateAfterBuy(price, quantity);
                        } else {
                                // 존재하지 않는 경우 새로 생성 (여기서 Duplicate Key 발생 가능)
                                userStock = UserStock.builder()
                                                .user(user)
                                                .stock(stock)
                                                .avgPrice(price)
                                                .totalQuantity(quantity)
                                                .ticker(stock.getTicker())
                                                .portfolio(portfolio)
                                                .userName(user.getName())
                                                .stockName(stock.getName())
                                                .build();
                                userStockRepository.saveAndFlush(userStock);
                        }
                } catch (DataIntegrityViolationException e) {
                        // 동시에 INSERT를 시도하다가 충돌난 경우, 다시 조회(Lock)하여 업데이트
                        userStock = userStockRepository.findByUserAndStockWithLock(user.getId(), stock.getId())
                                        .orElseThrow(() -> new RuntimeException("UserStock 조회 실패"));
                        entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);
                        userStock.updateAfterBuy(price, quantity);
                }

                userStock.increasePurchaseAmount((long) price * quantity);
                portfolio.addUserStock(userStock);
                updatePortfolioAfterBuy(portfolio, price, quantity);

                // 변경 사항을 즉시 DB에 보내 락 해제 전 정합성 확정
                entityManager.flush();
        }

        /*
         * 주식 즉시 매도
         */
        @Transactional
        public String sellingStock(Long userId, String ticker, int quantity) {

                // 유저
                User user = userRepository.findById(userId)
                                .orElseThrow(NotFoundUserException::new);

                portfolioRepository.findWithLockByUserId(user.getId())
                                .orElseThrow(NotFoundPortfolioException::new);

                // 주식
                Stock stock = stockRepository.findByTicker(ticker)
                                .orElseThrow(NotFoundStockException::new);

                // 보유 주식 확인
                UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                                .orElseThrow(NotEnoughStockException::new);

                if (userStock.getTotalQuantity() < quantity) {
                        throw new NotEnoughStockException(); // 보유량보다 매도량이 많으면 예외
                }

                // 주식 현재가
                int stockPrice = getStockPrice(ticker);

                // 총 매도 금액
                long totalPrice = (long) stockPrice * quantity;

                // 주문(Order) 생성
                Order newOrder = Order.builder()
                                .type(OrderType.SELL)
                                .price(stockPrice)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .user(user)
                                .stock(stock)
                                .isReserved(false)
                                .isExecuted(true)
                                .build();

                orderRepository.save(newOrder);

                stock.getOrders().add(newOrder);

                // 주문 체결
                executeSell(newOrder, user, stock, stockPrice, quantity, totalPrice);

                return stock.getName() + " 주식 " + quantity + "주 매도에 성공하였습니다!";
        }

        /*
         * 주문 체결 (매도)
         */
        @Transactional
        public void executeSell(Order order, User user, Stock stock, int price, int quantity, long totalPrice) {
                Execution execution = Execution.builder()
                                .order(order)
                                .type(OrderType.SELL)
                                .price(price)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .build();
                executionRepository.save(execution);

                Portfolio portfolio = portfolioRepository.findWithLockByUserId(user.getId())
                                .orElseThrow(NotFoundPortfolioException::new);
                entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

                UserStock userStock = userStockRepository.findByUserAndStockWithLock(user.getId(), stock.getId())
                                .orElseThrow(NotEnoughStockException::new);
                entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);

                // 보유 수량 확인 (예약 주문 체결 시점의 보유량 재확인)
                if (userStock.getTotalQuantity() < quantity) {
                        throw new NotEnoughStockException();
                }

                int remainingQuantity = userStock.getTotalQuantity() - quantity;

                // 수익 계산 (long 연산으로 오버플로우 방지)
                int avgPrice = userStock.getAvgPrice();
                long profit = (long) (price - avgPrice) * quantity;
                double returnRate = ((double) profit / ((long) avgPrice * quantity)) * 100; // 수익률 계산

                log.info("[매도체결] {} 매도 수익: {}원 (수익률: {}%)", stock.getName(), profit, returnRate);

                if (remainingQuantity > 0) { // 아직 남은 주식이 있다면
                        userStock.updateQuantity(remainingQuantity); // 수량 업데이트
                        userStock.decreasePurchaseAmount(quantity); // 매입원가 업데이트
                } else { // 모든 주식을 다 팔았다면
                         // 보유 주식에서 삭제
                        userStockRepository.delete(userStock);
                        user.getUserStocks().remove(userStock);
                        stock.getUserStocks().remove(userStock);
                        portfolio.getUserStocks().remove(userStock);
                }

                // 주식 매도 -> 포트폴리오 갱신
                updatePortfolioAfterSell(portfolio, price, quantity);

        }

        @Transactional
        private void updatePortfolioAfterBuy(Portfolio portfolio, int buyPrice, int quantity) {

                // 매수 후 보유 주식 평가액 증가
                long increaseStockValue = (long) buyPrice * quantity;
                portfolio.increaseStockAsset(increaseStockValue);

                // 잔액(현금) 감소
                portfolio.decreaseBalance(increaseStockValue);

                // 총 보유 수량 증가
                portfolio.increaseTotalQuantity(quantity);

                // 총 자산 재계산
                portfolio.recalculateTotalAsset();

                // 보유 종목 수 업데이트
                portfolio.updateHoldCount();
        }

        /*
         * 주식 매도로 인한 포토폴리오 업데이트
         */
        @Transactional
        private void updatePortfolioAfterSell(Portfolio portfolio, int sellPrice, int quantity) {

                // 매도 후 보유 주식 평가액 감소
                long decreasedStockValue = (long) sellPrice * quantity;
                portfolio.decreaseStockAsset(decreasedStockValue);

                // 잔액(현금) 증가
                portfolio.increaseBalance(decreasedStockValue);

                // 총 보유 수량 감소
                portfolio.decreaseTotalQuantity(quantity);

                // 총 자산 재계산
                portfolio.recalculateTotalAsset();

                // 수익률 업데이트
                // portfolio.updateReturnRate();

                portfolio.updateHoldCount();
        }

        /*
         * 예약 매수 등록 (특정 가격 이하로 떨어지면 매수)
         */
        @Transactional
        public String reserveBuy(Long userId, String ticker, int quantity, int targetPrice) {
                User user = userRepository.findById(userId)
                                .orElseThrow(NotFoundUserException::new);

                Portfolio portfolio = portfolioRepository.findWithLockByUserId(userId)
                                .orElseThrow(NotFoundPortfolioException::new);
                entityManager.refresh(portfolio);

                Stock stock = stockRepository.findByTicker(ticker)
                                .orElseThrow(NotFoundStockException::new);

                long totalPrice = (long) targetPrice * quantity;

                if (portfolio.getBalance() < totalPrice)
                        throw new NotEnoughMoneyException();

                Order reservedOrder = Order.builder()
                                .type(OrderType.BUY)
                                .price(targetPrice)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .user(user)
                                .stock(stock)
                                .isReserved(true)
                                .isExecuted(false)
                                .build();

                orderRepository.save(reservedOrder);

                return String.format("%s 주식 예약 매수 등록 완료 (%.0f원 이하 시 체결)", stock.getName(), (double) targetPrice);
        }

        /*
         * 예약 매도 등록 (특정 가격 이상 시 매도)
         */
        @Transactional
        public String reserveSell(Long userId, String ticker, int quantity, int targetPrice) {
                User user = userRepository.findById(userId)
                                .orElseThrow(NotFoundUserException::new);

                portfolioRepository.findWithLockByUserId(userId)
                                .orElseThrow(NotFoundPortfolioException::new);

                Stock stock = stockRepository.findByTicker(ticker)
                                .orElseThrow(NotFoundStockException::new);

                UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                                .orElseThrow(NotEnoughStockException::new);

                if (userStock.getTotalQuantity() < quantity)
                        throw new NotEnoughStockException();

                long totalPrice = (long) targetPrice * quantity;

                Order reservedOrder = Order.builder()
                                .type(OrderType.SELL)
                                .price(targetPrice)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .user(user)
                                .stock(stock)
                                .isReserved(true)
                                .isExecuted(false)
                                .build();

                orderRepository.save(reservedOrder);

                return String.format("%s 주식 예약 매도 등록 완료 (%.0f원 이상 시 체결)", stock.getName(), (double) targetPrice);
        }

        /*
         * 특정 종목의 예약 주문 체결 (이벤트 기반 - 주가 업데이트 시 호출)
         * WebSocket 스레드를 차단하지 않기 위해 비동기로 실행
         */
        @Async
        @Transactional
        public void executeReservedOrdersForTicker(String ticker, int currentPrice) {
                List<Order> reservedOrders = orderRepository.findReservedOrdersByTicker(ticker);

                if (reservedOrders.isEmpty()) {
                        return;
                }

                log.debug("예약 주문 체결 확인 - 종목: {}, 현재가: {}, 예약 주문 수: {}", ticker, currentPrice, reservedOrders.size());

                for (Order order : reservedOrders) {
                        // 예약 매수 조건: 현재가 <= 예약가
                        if (order.getType() == OrderType.BUY && currentPrice <= order.getPrice()) {
                                log.info("[예약매수체결조건만족] 주문 ID: {}, 종목: {}, 현재가: {} <= 예약가: {}",
                                                order.getId(), order.getStock().getName(), currentPrice,
                                                order.getPrice());

                                // ★ 이중 체결 방지: 비관적 잠금으로 DB 최신 상태 재확인
                                Order lockedOrder = orderRepository.findWithLockById(order.getId()).orElse(null);
                                if (lockedOrder == null || lockedOrder.isExecuted()) {
                                        log.warn("[예약매수이중체결방지] 주문 ID: {} 이미 체결됨 - 스킵", order.getId());
                                        continue;
                                }

                                try {
                                        executeBuy(lockedOrder, lockedOrder.getUser(), lockedOrder.getStock(), currentPrice,
                                                        lockedOrder.getQuantity(),
                                                        (long) currentPrice * lockedOrder.getQuantity());
                                        lockedOrder.markExecuted(); // 체결 완료로 갱신
                                        log.info("[예약매수체결] {}: {}원", lockedOrder.getStock().getName(), currentPrice);

                                        // 주문 체결 알림 전송
                                        Map<String, Object> notification = new HashMap<>();
                                        notification.put("type", "BUY");
                                        notification.put("message", String.format("%s 주식 예약 매수 체결 (체결가: %,d원)",
                                                        lockedOrder.getStock().getName(), currentPrice));
                                        notification.put("stockName", lockedOrder.getStock().getName());
                                        notification.put("stockTicker", lockedOrder.getStock().getTicker());
                                        notification.put("executionPrice", currentPrice);
                                        notification.put("quantity", lockedOrder.getQuantity());
                                        sessionManager.sendOrderNotification(lockedOrder.getUser().getId(),
                                                        notification);
                                        log.info("[예약매수알림전송] 사용자 ID: {}, 종목: {}", lockedOrder.getUser().getId(),
                                                        lockedOrder.getStock().getName());
                                } catch (NotEnoughMoneyException e) {
                                        log.warn("[예약매수체결실패] 주문 ID: {}, 사유: 잔액 부족", order.getId());
                                        // 잔액 부족 시 주문을 취소하거나 그대로 둘 수 있음. 여기서는 로그만 남기고 다음 기회로 넘김.
                                } catch (Exception e) {
                                        log.error("[예약매수체결오류] 주문 ID: {}, 오류: {}", order.getId(), e.getMessage());
                                }
                        }

                        // 예약 매도 조건: 현재가 >= 예약가
                        if (order.getType() == OrderType.SELL && currentPrice >= order.getPrice()) {
                                log.info("[예약매도체결조건만족] 주문 ID: {}, 종목: {}, 현재가: {} >= 예약가: {}",
                                                order.getId(), order.getStock().getName(), currentPrice,
                                                order.getPrice());

                                // ★ 이중 체결 방지: 비관적 잠금으로 DB 최신 상태 재확인
                                Order lockedOrder = orderRepository.findWithLockById(order.getId()).orElse(null);
                                if (lockedOrder == null || lockedOrder.isExecuted()) {
                                        log.warn("[예약매도이중체결방지] 주문 ID: {} 이미 체결됨 - 스킵", order.getId());
                                        continue;
                                }

                                try {
                                        executeSell(lockedOrder, lockedOrder.getUser(), lockedOrder.getStock(), currentPrice,
                                                        lockedOrder.getQuantity(),
                                                        (long) currentPrice * lockedOrder.getQuantity());
                                        lockedOrder.markExecuted();
                                        log.info("[예약매도체결] {}: {}원", lockedOrder.getStock().getName(), currentPrice);

                                        // 주문 체결 알림 전송
                                        Map<String, Object> notification = new HashMap<>();
                                        notification.put("type", "SELL");
                                        notification.put("message", String.format("%s 주식 예약 매도 체결 (체결가: %,d원)",
                                                        lockedOrder.getStock().getName(), currentPrice));
                                        notification.put("stockName", lockedOrder.getStock().getName());
                                        notification.put("stockTicker", lockedOrder.getStock().getTicker());
                                        notification.put("executionPrice", currentPrice);
                                        notification.put("quantity", lockedOrder.getQuantity());
                                        sessionManager.sendOrderNotification(lockedOrder.getUser().getId(),
                                                        notification);
                                        log.info("[예약매도알림전송] 사용자 ID: {}, 종목: {}", lockedOrder.getUser().getId(),
                                                        lockedOrder.getStock().getName());
                                } catch (NotEnoughStockException e) {
                                        log.warn("[예약매도체결실패] 주문 ID: {}, 사유: 주식 부족", order.getId());
                                } catch (Exception e) {
                                        log.error("[예약매도체결오류] 주문 ID: {}, 오류: {}", order.getId(), e.getMessage());
                                }
                        }
                }
        }

        /*
         * 예약 주문 자동 체결 스케줄러 (백업용 - 10초마다 실행)
         * 주가 업데이트가 없는 경우를 대비한 안전장치
         */
        @Scheduled(fixedDelay = 10000) // 10초마다 반복 (백업용)
        @Transactional
        public void executeReservedOrders() {
                List<Order> reservedOrders = orderRepository.findAllByIsReservedTrueAndIsExecutedFalse();

                if (reservedOrders.isEmpty()) {
                        return;
                }

                log.debug("예약 주문 체결 확인 중 (스케줄러) - 대기 중인 예약 주문 수: {}", reservedOrders.size());

                for (Order order : reservedOrders) {
                        try {
                                int currentPrice = getStockPrice(order.getStock().getTicker());
                                log.debug("예약 주문 체결 확인 - 주문 ID: {}, 종목: {}, 현재가: {}, 예약가: {}, 주문타입: {}",
                                                order.getId(), order.getStock().getName(), currentPrice,
                                                order.getPrice(), order.getType());

                                // 예약 매수 조건: 현재가 <= 예약가
                                if (order.getType() == OrderType.BUY && currentPrice <= order.getPrice()) {
                                        log.info("[예약매수체결조건만족-스케줄러] 주문 ID: {}, 종목: {}, 현재가: {} <= 예약가: {}",
                                                        order.getId(), order.getStock().getName(), currentPrice,
                                                        order.getPrice());

                                        // ★ 이중 체결 방지: 비관적 잠금으로 DB 최신 상태 재확인
                                        Order lockedOrder = orderRepository.findWithLockById(order.getId())
                                                        .orElse(null);
                                        if (lockedOrder == null || lockedOrder.isExecuted()) {
                                                log.warn("[예약매수이중체결방지-스케줄러] 주문 ID: {} 이미 체결됨 - 스킵", order.getId());
                                                continue;
                                        }

                                        try {
                                                executeBuy(lockedOrder, lockedOrder.getUser(), lockedOrder.getStock(),
                                                                currentPrice,
                                                                lockedOrder.getQuantity(),
                                                                (long) currentPrice * lockedOrder.getQuantity());
                                                lockedOrder.markExecuted();
                                                log.info("[예약매수체결-스케줄러] {}: {}원", lockedOrder.getStock().getName(),
                                                                currentPrice);

                                                // 주문 체결 알림 전송
                                                Map<String, Object> notification = new HashMap<>();
                                                notification.put("type", "BUY");
                                                notification.put("message", String.format("%s 주식 예약 매수 체결 (체결가: %,d원)",
                                                                lockedOrder.getStock().getName(), currentPrice));
                                                notification.put("stockName", lockedOrder.getStock().getName());
                                                notification.put("stockTicker", lockedOrder.getStock().getTicker());
                                                notification.put("executionPrice", currentPrice);
                                                notification.put("quantity", lockedOrder.getQuantity());
                                                sessionManager.sendOrderNotification(lockedOrder.getUser().getId(),
                                                                notification);
                                        } catch (NotEnoughMoneyException e) {
                                                log.warn("[예약매수체결실패-스케줄러] 주문 ID: {}, 사유: 잔액 부족", order.getId());
                                        } catch (Exception e) {
                                                log.error("[예약매수체결오류-스케줄러] 주문 ID: {}, 오류: {}", order.getId(),
                                                                e.getMessage());
                                        }
                                }

                                // 예약 매도 조건: 현재가 >= 예약가
                                if (order.getType() == OrderType.SELL && currentPrice >= order.getPrice()) {
                                        log.info("[예약매도체결조건만족-스케줄러] 주문 ID: {}, 종목: {}, 현재가: {} >= 예약가: {}",
                                                        order.getId(), order.getStock().getName(), currentPrice,
                                                        order.getPrice());

                                        // ★ 이중 체결 방지: 비관적 잠금으로 DB 최신 상태 재확인
                                        Order lockedOrder = orderRepository.findWithLockById(order.getId())
                                                        .orElse(null);
                                        if (lockedOrder == null || lockedOrder.isExecuted()) {
                                                log.warn("[예약매도이중체결방지-스케줄러] 주문 ID: {} 이미 체결됨 - 스킵", order.getId());
                                                continue;
                                        }

                                        try {
                                                executeSell(lockedOrder, lockedOrder.getUser(), lockedOrder.getStock(),
                                                                currentPrice,
                                                                lockedOrder.getQuantity(),
                                                                (long) currentPrice * lockedOrder.getQuantity());
                                                lockedOrder.markExecuted();
                                                log.info("[예약매도체결-스케줄러] {}: {}원", lockedOrder.getStock().getName(),
                                                                currentPrice);

                                                // 주문 체결 알림 전송
                                                Map<String, Object> notification = new HashMap<>();
                                                notification.put("type", "SELL");
                                                notification.put("message", String.format("%s 주식 예약 매도 체결 (체결가: %,d원)",
                                                                lockedOrder.getStock().getName(), currentPrice));
                                                notification.put("stockName", lockedOrder.getStock().getName());
                                                notification.put("stockTicker", lockedOrder.getStock().getTicker());
                                                notification.put("executionPrice", currentPrice);
                                                notification.put("quantity", lockedOrder.getQuantity());
                                                sessionManager.sendOrderNotification(lockedOrder.getUser().getId(),
                                                                notification);
                                        } catch (NotEnoughStockException e) {
                                                log.warn("[예약매도체결실패-스케줄러] 주문 ID: {}, 사유: 주식 부족", order.getId());
                                        } catch (Exception e) {
                                                log.error("[예약매도체결오류-스케줄러] 주문 ID: {}, 오류: {}", order.getId(),
                                                                e.getMessage());
                                        }
                                }
                        } catch (Exception e) {
                                log.error("예약 주문 체결 확인 중 오류 - 주문 ID: {}, 종목: {}, 오류: {}",
                                                order.getId(), order.getStock().getName(), e.getMessage());
                        }
                }
        }

        /*
         * 내 주문 전체 내역 조회
         */
        public List<OrderResponse> getMyAllOrders(Long userId) {
                List<Order> myOrders = orderRepository.findByUserId(userId);

                return myOrders.stream().map(OrderResponse::of)
                                .collect(Collectors.toList());
        }

        /*
         * 내 일반 주문(즉시 주문) 내역 조회
         */
        public List<OrderResponse> getNormalOrders(Long userId) {
                List<Order> orders = orderRepository.findNormalOrdersByUser(userId);

                return orders.stream().map(OrderResponse::of)
                                .collect(Collectors.toList());

        }

        /*
         * 내 예약 주문 내역 조회
         */
        public List<OrderResponse> getReservationOrders(Long userId) {
                List<Order> orders = orderRepository.findReservationOrdersByUser(userId);

                return orders.stream().map(OrderResponse::of)
                                .collect(Collectors.toList());

        }

        /*
         * 예약 주문 취소
         */
        @Transactional
        public OrderResponse cancelReservation(Long orderId) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(NotFoundOrderException::new);

                // 이미 체결된 주문이라면
                if (order.isExecuted() || !order.isReserved())
                        throw new ExecutedOrderException();

                // 주문 내역 삭제
                orderRepository.delete(order);

                return OrderResponse.of(order);
        }

        /*
         * Redis에서 주식 실시간 현재가 꺼내기
         */
        public int getStockPrice(String ticker) {
                String key = "stock:data:" + ticker;
                String json = redisTemplate.opsForValue().get(key);

                if (json == null)
                        throw new IllegalStateException("데이터 없음");

                try {
                        StockData data = objectMapper.readValue(json, StockData.class);
                        return data.getPrice();
                } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                }
        }

}
