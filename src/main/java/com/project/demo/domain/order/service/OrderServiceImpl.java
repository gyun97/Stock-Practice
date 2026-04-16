package com.project.demo.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.order.ExecutedOrderException;
import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.order.NotFoundOrderException;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.common.exception.stock.NotFoundStockException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.execution.service.ExecutionService;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
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
        private final ExecutionService executionService;

        @PersistenceContext
        private EntityManager entityManager;

        /*
         * 주식 즉시 매수
         */
        @Transactional
        public String buyingStock(Long userId, String ticker, int quantity) {
                User user = userRepository.findById(userId)
                                .orElseThrow(NotFoundUserException::new);

                portfolioRepository.findWithLockByUser(user)
                                .orElseThrow(NotFoundPortfolioException::new);

                Stock stock = stockRepository.findByTicker(ticker)
                                .orElseThrow(NotFoundStockException::new);

                int stockPrice = getStockPrice(ticker);
                long totalPrice = (long) stockPrice * quantity;

                Order newOrder = Order.builder()
                                .type(OrderType.BUY)
                                .price(stockPrice)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .user(user)
                                .stock(stock)
                                .isExecuted(false)
                                .isReserved(false)
                                .build();

                orderRepository.save(newOrder);
                entityManager.flush(); // 독립 트랜잭션에서 Order를 조회할 수 있도록 플러시

                // 주문 체결 (독립 트랜잭션 호출)
                executionService.executeBuy(newOrder.getId(), user, stock, stockPrice, quantity, totalPrice);

                return stock.getName() + " 주식 " + newOrder.getQuantity() + "주 구매에 성공하였습니다!";
        }

        /*
         * 주식 즉시 매도
         */
        @Transactional
        public String sellingStock(Long userId, String ticker, int quantity) {
                User user = userRepository.findById(userId)
                                .orElseThrow(NotFoundUserException::new);

                portfolioRepository.findWithLockByUserId(user.getId())
                                .orElseThrow(NotFoundPortfolioException::new);

                Stock stock = stockRepository.findByTicker(ticker)
                                .orElseThrow(NotFoundStockException::new);

                UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                                .orElseThrow(NotEnoughStockException::new);

                if (userStock.getTotalQuantity() < quantity) {
                        throw new NotEnoughStockException();
                }

                int stockPrice = getStockPrice(ticker);
                long totalPrice = (long) stockPrice * quantity;

                Order newOrder = Order.builder()
                                .type(OrderType.SELL)
                                .price(stockPrice)
                                .quantity(quantity)
                                .totalPrice(totalPrice)
                                .user(user)
                                .stock(stock)
                                .isReserved(false)
                                .isExecuted(false)
                                .build();

                orderRepository.save(newOrder);
                entityManager.flush(); // 독립 트랜잭션에서 Order를 조회할 수 있도록 플러시

                // 주문 체결 (독립 트랜잭션 호출)
                executionService.executeSell(newOrder.getId(), user, stock, stockPrice, quantity, totalPrice);

                return stock.getName() + " 주식 " + quantity + "주 매도에 성공하였습니다!";
        }

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

                // Redis ZSet 동기화
                String redisKey = "order:reserved:buy:" + ticker;
                redisTemplate.opsForZSet().add(redisKey, reservedOrder.getId().toString(), targetPrice);

                return String.format("%s 주식 예약 매수 등록 완료 (%.0f원 이하 시 체결)", stock.getName(), (double) targetPrice);
        }

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

                // Redis ZSet 동기화
                String redisKey = "order:reserved:sell:" + ticker;
                redisTemplate.opsForZSet().add(redisKey, reservedOrder.getId().toString(), targetPrice);

                return String.format("%s 주식 예약 매도 등록 완료 (%.0f원 이상 시 체결)", stock.getName(), (double) targetPrice);
        }

        public List<OrderResponse> getMyAllOrders(Long userId) {
                List<Order> myOrders = orderRepository.findByUserId(userId);
                return myOrders.stream().map(OrderResponse::of).collect(Collectors.toList());
        }

        public List<OrderResponse> getNormalOrders(Long userId) {
                List<Order> orders = orderRepository.findNormalOrdersByUser(userId);
                return orders.stream().map(OrderResponse::of).collect(Collectors.toList());
        }

        public List<OrderResponse> getReservationOrders(Long userId) {
                List<Order> orders = orderRepository.findReservationOrdersByUser(userId);
                return orders.stream().map(OrderResponse::of).collect(Collectors.toList());
        }

        @Transactional
        public OrderResponse cancelReservation(Long orderId) {
                Order order = orderRepository.findById(orderId)
                                .orElseThrow(NotFoundOrderException::new);

                if (order.isExecuted() || !order.isReserved())
                        throw new ExecutedOrderException();

                orderRepository.delete(order);
                return OrderResponse.of(order);
        }

        public int getStockPrice(String ticker) {
                String key = "stock:data:" + ticker;
                String json = redisTemplate.opsForValue().get(key);

                if (json == null)
                        throw new IllegalStateException("데이터 없음");

                try {
                        StockData data = objectMapper.readValue(json, StockData.class);
                        return data.getPrice();
                } catch (Exception e) {
                        throw new RuntimeException(e);
                }
        }
}
