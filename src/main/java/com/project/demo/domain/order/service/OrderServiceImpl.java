package com.project.demo.domain.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.order.ExecutedOrderException;
import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.order.NotFoundOrderException;
import com.project.demo.common.exception.stock.NotFoundStockException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.domain.execution.entity.Execution;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.stock.dto.response.StockData;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService{

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserStockRepository userStockRepository;
    private final StockRepository stockRepository;
    private final ExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /*
    주식 즉시 매수
     */
    @Transactional
    public String buyingStock(Long userId, String ticker, int quantity) {

        // 유저
        User user = userRepository.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(NotFoundStockException::new);

        // 주식 현재가
        int stockPrice = getStockPrice(ticker);

        // 총 주문 가격
        int totalPrice = stockPrice * quantity;

        // 보유금이 모자르다면
        if (user.getBalance() < totalPrice) {
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

        // 주문 체결
        executeBuy(newOrder, user, stock, stockPrice, quantity, totalPrice);

        return stock.getName() + " 주식 " + newOrder.getQuantity() + "주 구매에 성공하였습니다!";
    }

    /*
    매수 주문 체결
     */
    @Transactional
    public void executeBuy(Order order, User user, Stock stock, int price, int quantity, int totalPrice) {
        // 유저 보유액 차감
        user.deductBalance(totalPrice);

        // 체결 생성
        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.BUY)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();

        executionRepository.save(execution);

        // 기존 보유 주식(UserStock) 확인
        Optional<UserStock> optionalUserStock = userStockRepository.findByUserAndStock(user, stock);

        if (optionalUserStock.isPresent()) { // 만약 해당 종목의 주식을 이전에 이미 구매했다면
            // 해당 종목의 평균 단가 갱신
            UserStock userStock = optionalUserStock.get();

            int currentQuantity = userStock.getTotalQuantity();
            int currentAvgPrice = userStock.getAvgPrice();

            int newTotalQuantity = currentQuantity + quantity;
            int newAvgPrice = (currentAvgPrice * currentQuantity + price * quantity) / newTotalQuantity;

            userStock.updateAveragePrice(newAvgPrice); // 평균 구매 갱신
            userStock.updateQuantity(newTotalQuantity);

        } else {
            // 신규 보유 주식 생성
            UserStock userStock = UserStock.builder()
                    .user(user)
                    .stock(stock)
                    .avgPrice(price)
                    .totalQuantity(quantity)
                    .totalAsset(price * quantity)
                    .avgReturnRate(0.0)
                    .build();
            userStockRepository.save(userStock);
        }
    }

    /*
    주식 즉시 매도
    */
    @Transactional
    public String sellingStock(Long userId, String ticker, int quantity) {

        // 유저
        User user = userRepository.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        // 주식
        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(NotFoundStockException::new);

        // 보유 주식 확인
        UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                .orElseThrow(NotEnoughStockException::new);

        if (userStock.getTotalQuantity() < quantity) {
            throw new NotEnoughStockException(); // 보유량보다 매수량이 많으면 예외
        }

        // 주식 현재가
        int stockPrice = getStockPrice(ticker);

        // 총 매도 금액
        int totalPrice = stockPrice * quantity;

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

        // 주문 체결
        executeSell(newOrder, user, stock, stockPrice, quantity, totalPrice);

        return stock.getName() + " 주식 " + quantity + "주 매도에 성공하였습니다!";
    }

    /*
    주문 체결 (매도)
    */
    @Transactional
    public void executeSell(Order order, User user, Stock stock, int price, int quantity, int totalPrice) {
        user.addBalance(totalPrice);

        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.SELL)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();
        executionRepository.save(execution);

        UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                .orElseThrow(NotEnoughStockException::new);

        int remainingQuantity = userStock.getTotalQuantity() - quantity;

        // 차익 계산
        int avgPrice = userStock.getAvgPrice();
        int profit = (price - avgPrice) * quantity;
        double returnRate = ((double) profit / (avgPrice * quantity)) * 100; // 수익률 계산

        log.info("[매도체결] {} 매도 수익: {}원 (수익률: {}%)", stock.getName(), profit, returnRate);

        if (remainingQuantity > 0) { // 아직 남은 주식이 있다면
            userStock.updateQuantity(remainingQuantity); // 수량 업데이트
        } else { // 모든 주식을 다 팔았다면
            userStockRepository.delete(userStock); // 보유 주식에서 삭제
        }
    }

    /*
    예약 매수 등록 (특정 가격 이하로 떨어지면 매수)
    */
    @Transactional
    public String reserveBuy(Long userId, String ticker, int quantity, int targetPrice) {
        User user = userRepository.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(NotFoundStockException::new);

        int totalPrice = targetPrice * quantity;

        if (user.getBalance() < totalPrice) throw new NotEnoughMoneyException();

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
    예약 매도 등록 (특정 가격 이상 시 매도)
    */
    @Transactional
    public String reserveSell(Long userId, String ticker, int quantity, int targetPrice) {
        User user = userRepository.findById(userId)
                .orElseThrow(NotFoundUserException::new);

        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(NotFoundStockException::new);

        UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                .orElseThrow(NotEnoughStockException::new);

        if (userStock.getTotalQuantity() < quantity) throw new NotEnoughStockException();

        int totalPrice = targetPrice * quantity;

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
    예약 주문 자동 체결 스케줄러
     */
    @Scheduled(fixedDelay = 5000) // 5초마다 반복
    @Transactional
    public void executeReservedOrders() {
        List<Order> reservedOrders = orderRepository.findAllByIsReservedTrueAndIsExecutedFalse();

        for (Order order : reservedOrders) {
            int currentPrice = getStockPrice(order.getStock().getTicker());

            // 예약 매수 조건: 현재가 <= 예약가
            if (order.getType() == OrderType.BUY && currentPrice <= order.getPrice()) {
                executeBuy(order, order.getUser(), order.getStock(), currentPrice,
                        order.getQuantity(), currentPrice * order.getQuantity());
                order.markExecuted(); // 체결 완료로 갱신
                log.info("[예약매수체결] {}: {}원", order.getStock().getName(), currentPrice);
            }

            // 예약 매도 조건: 현재가 >= 예약가
            if (order.getType() == OrderType.SELL && currentPrice >= order.getPrice()) {
                executeSell(order, order.getUser(), order.getStock(), currentPrice,
                        order.getQuantity(), currentPrice * order.getQuantity());
                order.markExecuted();
                log.info("[예약매도체결] {}: {}원", order.getStock().getName(), currentPrice);
            }
        }
    }

    /*
    내 주문 전체 내역 조회
     */
    public List<OrderResponse> getMyAllOrders(Long userId) {
        List<Order> myOrders = orderRepository.findByUserId(userId);

        return myOrders.stream().map(OrderResponse::of)
                .collect(Collectors.toList());
    }

    /*
    내 일반 주문(즉시 주문) 내역 조회
     */
    public List<OrderResponse> getNormalOrders(Long userId) {
        List<Order> orders = orderRepository.findNormalOrdersByUser(userId);

        return orders.stream().map(OrderResponse::of)
                .collect(Collectors.toList());

    }

    /*
    내 예약 주문 내역 조회
     */
    public List<OrderResponse> getReservationOrders(Long userId) {
        List<Order> orders = orderRepository.findReservationOrdersByUser(userId);

        return orders.stream().map(OrderResponse::of)
                .collect(Collectors.toList());

    }

    /*
    예약 주문 취소
     */
    @Transactional
    public OrderResponse cancelReservation(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(NotFoundOrderException::new);

        // 이미 체결된 주문이라면
        if (order.isExecuted() || !order.isReserved()) throw new ExecutedOrderException();

        // 주문 내역 삭제
        orderRepository.delete(order);

        return OrderResponse.of(order);
    }

    /*
    Redis에서 주식 실시간 현재가 꺼내기
     */
    public int getStockPrice(String ticker) {
        String key = "stock:data:" + ticker;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) throw new IllegalStateException("데이터 없음");

        try {
            StockData data = objectMapper.readValue(json, StockData.class);
            return data.getPrice();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }



}
