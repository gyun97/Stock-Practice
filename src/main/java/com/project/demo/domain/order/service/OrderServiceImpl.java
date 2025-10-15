package com.project.demo.domain.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.stock.NotFoundStockException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.domain.execution.entity.Execution;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.stock.dto.response.StockData;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.user.service.UserService;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService{

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserService userService;
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
                .orElseThrow(() -> new NotFoundUserException());

        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new NotFoundStockException());

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

            userStock.updateAveragePrice(newAvgPrice);
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
                .orElseThrow(() -> new NotFoundUserException());

        // 주식
        Stock stock = stockRepository.findByTicker(ticker)
                .orElseThrow(() -> new NotFoundStockException());

        // 보유 주식 확인
        UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new NotEnoughStockException());

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

        // 유저 잔액 증가
        user.addBalance(totalPrice);

        // 체결 생성
        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.SELL)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();

        executionRepository.save(execution);

        // UserStock 갱신
        UserStock userStock = userStockRepository.findByUserAndStock(user, stock)
                .orElseThrow(() -> new NotEnoughStockException());

        int remainingQuantity = userStock.getTotalQuantity() - quantity;

        if (remainingQuantity > 0) {
            userStock.updateQuantity(remainingQuantity);
        } else {
            // 남은 수량 없으면 삭제
            userStockRepository.delete(userStock);
        }
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
