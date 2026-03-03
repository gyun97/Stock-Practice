package com.project.demo.domain.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.order.ExecutedOrderException;
import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.order.NotFoundOrderException;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.common.exception.stock.NotFoundStockException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.common.websocket.WebSocketSessionManager;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import com.project.demo.common.oauth2.SocialType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OrderServiceImpl 단위 테스트
 */
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStockRepository userStockRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WebSocketSessionManager sessionManager;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User testUser;
    private Stock testStock;
    private Portfolio testPortfolio;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .password("password")
                .name("테스트 사용자")
                .isDeleted(false)
                .withdrawalAt(null)
                .userRole(UserRole.ROLE_USER)
                .socialType(SocialType.LOCAL)
                .socialId(null)
                .email("test@example.com")
                .profileImage("")
                .orders(new ArrayList<>())
                .userStocks(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testUser, "userStocks", new ArrayList<>());

        testStock = Stock.builder()
                .ticker("005930")
                .name("삼성전자")
                .build();
        ReflectionTestUtils.setField(testStock, "id", 1L);
        ReflectionTestUtils.setField(testStock, "orders", new ArrayList<>());
        ReflectionTestUtils.setField(testStock, "userStocks", new ArrayList<>());

        testPortfolio = Portfolio.builder()
                .id(1L)
                .balance(20000000L) // 충분한 잔액 설정 (2000만원)
                .stockAsset(700000L)
                .totalAsset(20000000L)
                .holdCount(1L)
                .totalQuantity(10L)
                .user(testUser)
                .userStocks(new ArrayList<>())
                .build();
        ReflectionTestUtils.setField(testPortfolio, "userStocks", new ArrayList<>());
    }

    @Test
    void 주식_매수_성공_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 10;
        int stockPrice = 70000;
        int totalPrice = stockPrice * quantity;

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn("{\"price\":" + stockPrice + "}");
        // getStockPrice에서 ObjectMapper 사용
        com.project.demo.domain.stock.dto.response.StockData stockData = com.project.demo.domain.stock.dto.response.StockData
                .builder()
                .price(stockPrice)
                .build();
        try {
            when(objectMapper.readValue(eq("{\"price\":" + stockPrice + "}"),
                    eq(com.project.demo.domain.stock.dto.response.StockData.class)))
                    .thenReturn(stockData);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 1L);
            return order;
        });
        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.empty());
        when(userStockRepository.save(any(UserStock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String result = orderService.buyingStock(userId, ticker, quantity);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("구매에 성공"));
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(executionRepository, times(1)).save(any());
    }

    @Test
    void 주식_매수_사용자_없음_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 10;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundUserException.class, () -> {
            orderService.buyingStock(userId, ticker, quantity);
        });
    }

    @Test
    void 주식_매수_주식_없음_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 10;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundStockException.class, () -> {
            orderService.buyingStock(userId, ticker, quantity);
        });
    }

    @Test
    void 주식_매수_잔액_부족_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 1000; // 매우 큰 수량
        int stockPrice = 70000;
        ReflectionTestUtils.setField(testPortfolio, "balance", 1000L); // 잔액 부족

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn("{\"price\":" + stockPrice + "}");
        // getStockPrice에서 ObjectMapper 사용
        com.project.demo.domain.stock.dto.response.StockData stockData2 = com.project.demo.domain.stock.dto.response.StockData
                .builder()
                .price(stockPrice)
                .build();
        try {
            when(objectMapper.readValue(eq("{\"price\":" + stockPrice + "}"),
                    eq(com.project.demo.domain.stock.dto.response.StockData.class)))
                    .thenReturn(stockData2);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }

        // When & Then
        assertThrows(NotEnoughMoneyException.class, () -> {
            orderService.buyingStock(userId, ticker, quantity);
        });
    }

    @Test
    void 예약_주문_취소_테스트() {
        // Given
        Long orderId = 1L;
        Order order = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(false)
                .isReserved(true)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);

        // 테스트용 Portfolio 생성
        Portfolio testPortfolio = Portfolio.builder()
                .id(1L)
                .balance(10000000L)
                .stockAsset(0L)
                .totalAsset(10000000L)
                .holdCount(0L)
                .totalQuantity(0L)
                .user(testUser)
                .userStocks(new ArrayList<>())
                .build();
        lenient().when(portfolioRepository.findByUser(any(User.class))).thenReturn(Optional.of(testPortfolio));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // When
        OrderResponse response = orderService.cancelReservation(orderId);

        // Then
        assertNotNull(response);
        verify(orderRepository, times(1)).delete(order);
    }

    @Test
    void 예약_주문_취소_주문_없음_예외_테스트() {
        // Given
        Long orderId = 1L;
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundOrderException.class, () -> {
            orderService.cancelReservation(orderId);
        });
    }

    @Test
    void 예약_주문_취소_이미_체결된_주문_예외_테스트() {
        // Given
        Long orderId = 1L;
        Order executedOrder = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(true) // 이미 체결됨
                .isReserved(true)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(executedOrder, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(executedOrder));

        // When & Then
        assertThrows(ExecutedOrderException.class, () -> {
            orderService.cancelReservation(orderId);
        });
    }

    @Test
    void 예약_주문_취소_일반_주문_예외_테스트() {
        // Given
        Long orderId = 1L;
        Order normalOrder = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(true)
                .isReserved(false) // 예약 주문이 아님
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(normalOrder, "id", orderId);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(normalOrder));

        // When & Then
        assertThrows(ExecutedOrderException.class, () -> {
            orderService.cancelReservation(orderId);
        });
    }

    @Test
    void 주식_매도_성공_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 5;
        int stockPrice = 75000;
        int totalPrice = stockPrice * quantity;

        UserStock userStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker(ticker)
                .avgPrice(70000)
                .totalQuantity(10)
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .build();
        ReflectionTestUtils.setField(userStock, "id", 1L);

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.of(userStock));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn("{\"price\":" + stockPrice + "}");
        com.project.demo.domain.stock.dto.response.StockData stockData = com.project.demo.domain.stock.dto.response.StockData
                .builder()
                .price(stockPrice)
                .build();
        try {
            when(objectMapper.readValue(eq("{\"price\":" + stockPrice + "}"),
                    eq(com.project.demo.domain.stock.dto.response.StockData.class)))
                    .thenReturn(stockData);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 1L);
            return order;
        });
        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        lenient().when(userStockRepository.save(any(UserStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        String result = orderService.sellingStock(userId, ticker, quantity);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("매도에 성공"));
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(executionRepository, times(1)).save(any());
    }

    @Test
    void 주식_매도_보유_주식_없음_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 5;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotEnoughStockException.class, () -> {
            orderService.sellingStock(userId, ticker, quantity);
        });
    }

    @Test
    void 주식_매도_보유량_부족_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 100; // 보유량보다 많음

        UserStock userStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker(ticker)
                .avgPrice(70000)
                .totalQuantity(5) // 보유량이 적음
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.of(userStock));

        // When & Then
        assertThrows(NotEnoughStockException.class, () -> {
            orderService.sellingStock(userId, ticker, quantity);
        });
    }

    @Test
    void 예약_매수_등록_성공_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 10;
        int targetPrice = 70000;
        int totalPrice = targetPrice * quantity;

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 1L);
            return order;
        });

        // When
        String result = orderService.reserveBuy(userId, ticker, quantity, targetPrice);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("예약 매수"));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void 예약_매수_잔액_부족_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 1000;
        int targetPrice = 70000;
        ReflectionTestUtils.setField(testPortfolio, "balance", 1000L); // 잔액 부족

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));

        // When & Then
        assertThrows(NotEnoughMoneyException.class, () -> {
            orderService.reserveBuy(userId, ticker, quantity, targetPrice);
        });
    }

    @Test
    void 예약_매도_등록_성공_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 5;
        int targetPrice = 75000;

        UserStock userStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker(ticker)
                .avgPrice(70000)
                .totalQuantity(10)
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(any(User.class), any(Stock.class)))
                .thenReturn(Optional.of(userStock));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 1L);
            return order;
        });

        // When
        String result = orderService.reserveSell(userId, ticker, quantity, targetPrice);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("예약 매도"));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void 예약_매도_보유_주식_부족_예외_테스트() {
        // Given
        Long userId = 1L;
        String ticker = "005930";
        int quantity = 100;
        int targetPrice = 75000;

        UserStock userStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker(ticker)
                .avgPrice(70000)
                .totalQuantity(5) // 보유량이 적음
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(stockRepository.findByTicker(ticker)).thenReturn(Optional.of(testStock));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.empty()); // 보유 주식 없음

        // When & Then
        assertThrows(NotEnoughStockException.class, () -> {
            orderService.reserveSell(userId, ticker, quantity, targetPrice);
        });
    }

    @Test
    void 내_주문_내역_전체_조회_테스트() {
        // Given
        Long userId = 1L;
        Order order1 = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order1, "id", 1L);

        Order order2 = Order.builder()
                .type(OrderType.SELL)
                .price(75000)
                .quantity(5)
                .totalPrice(375000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order2, "id", 2L);

        when(orderRepository.findByUserId(userId))
                .thenReturn(Arrays.asList(order1, order2));

        // When
        List<OrderResponse> result = orderService.getMyAllOrders(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(orderRepository, times(1)).findByUserId(userId);
    }

    @Test
    void 내_일반_주문_내역_조회_테스트() {
        // Given
        Long userId = 1L;
        Order order = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findNormalOrdersByUser(userId))
                .thenReturn(Arrays.asList(order));

        // When
        List<OrderResponse> result = orderService.getNormalOrders(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertFalse(result.get(0).isReserved());
        verify(orderRepository, times(1)).findNormalOrdersByUser(userId);
    }

    @Test
    void 내_예약_주문_내역_조회_테스트() {
        // Given
        Long userId = 1L;
        Order order = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(false)
                .isReserved(true)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);

        when(orderRepository.findReservationOrdersByUser(userId))
                .thenReturn(Arrays.asList(order));

        // When
        List<OrderResponse> result = orderService.getReservationOrders(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).isReserved());
        verify(orderRepository, times(1)).findReservationOrdersByUser(userId);
    }

    @Test
    void 매수_주문_체결_신규_보유주식_생성_테스트() {
        // Given
        Order order = Order.builder()
                .type(OrderType.BUY)
                .price(70000)
                .quantity(10)
                .totalPrice(700000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);

        int price = 70000;
        int quantity = 10;
        int totalPrice = 700000;

        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.empty());
        when(userStockRepository.save(any(UserStock.class))).thenAnswer(invocation -> {
            UserStock userStock = invocation.getArgument(0);
            ReflectionTestUtils.setField(userStock, "id", 1L);
            return userStock;
        });
        when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        long initialBalance = testPortfolio.getBalance();
        orderService.executeBuy(order, testUser, testStock, price, quantity, totalPrice);

        // Then
        assertEquals(initialBalance - totalPrice, testPortfolio.getBalance()); // 잔액 차감 확인
        verify(executionRepository, times(1)).save(any());
        verify(userStockRepository, times(1)).save(any(UserStock.class));
    }

    @Test
    void 매수_주문_체결_기존_보유주식_업데이트_테스트() {
        // Given
        Order order = Order.builder()
                .type(OrderType.BUY)
                .price(75000)
                .quantity(5)
                .totalPrice(375000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);

        int price = 75000;
        int quantity = 5;
        int totalPrice = 375000;

        UserStock existingUserStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker("005930")
                .avgPrice(70000)
                .totalQuantity(10)
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .portfolio(testPortfolio)
                .build();
        ReflectionTestUtils.setField(existingUserStock, "id", 1L);

        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(testUser, testStock))
                .thenReturn(Optional.of(existingUserStock));
        lenient().when(userStockRepository.save(any(UserStock.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        long initialBalance = testPortfolio.getBalance();
        orderService.executeBuy(order, testUser, testStock, price, quantity, totalPrice);

        // Then
        assertEquals(initialBalance - totalPrice, testPortfolio.getBalance()); // 잔액 차감 확인
        verify(executionRepository, times(1)).save(any());
        assertEquals(15, existingUserStock.getTotalQuantity()); // 10 + 5
    }

    @Test
    void 매도_주문_체결_일부_매도_테스트() {
        // Given
        Order order = Order.builder()
                .type(OrderType.SELL)
                .price(75000)
                .quantity(5)
                .totalPrice(375000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);

        int price = 75000;
        int quantity = 5;
        int totalPrice = 375000;

        UserStock userStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker("005930")
                .avgPrice(70000)
                .totalQuantity(10)
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .portfolio(testPortfolio)
                .build();
        ReflectionTestUtils.setField(userStock, "id", 1L);

        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(testUser, testStock))
                .thenReturn(Optional.of(userStock));
        when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        long initialBalance = testPortfolio.getBalance();
        orderService.executeSell(order, testUser, testStock, price, quantity, totalPrice);

        // Then
        assertEquals(initialBalance + totalPrice, testPortfolio.getBalance()); // 잔액 증가 확인
        verify(executionRepository, times(1)).save(any());
        assertEquals(5, userStock.getTotalQuantity()); // 10 - 5
        verify(userStockRepository, never()).delete(any(UserStock.class));
    }

    @Test
    void 매도_주문_체결_전량_매도_테스트() {
        // Given
        Order order = Order.builder()
                .type(OrderType.SELL)
                .price(75000)
                .quantity(10)
                .totalPrice(750000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();
        ReflectionTestUtils.setField(order, "id", 1L);

        int price = 75000;
        int quantity = 10;
        int totalPrice = 750000;

        UserStock userStock = UserStock.builder()
                .user(testUser)
                .stock(testStock)
                .ticker("005930")
                .avgPrice(70000)
                .totalQuantity(10)
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .portfolio(testPortfolio)
                .build();
        ReflectionTestUtils.setField(userStock, "id", 1L);

        when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(testUser, testStock))
                .thenReturn(Optional.of(userStock));
        doNothing().when(userStockRepository).delete(userStock);
        when(executionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        long initialBalance = testPortfolio.getBalance();
        orderService.executeSell(order, testUser, testStock, price, quantity, totalPrice);

        // Then
        assertEquals(initialBalance + totalPrice, testPortfolio.getBalance()); // 잔액 증가 확인
        verify(executionRepository, times(1)).save(any());
        verify(userStockRepository, times(1)).delete(userStock);
    }

    @Test
    void 매도_주문_체결_보유주식_없음_예외_테스트() {
        // Given
        Order order = Order.builder()
                .type(OrderType.SELL)
                .price(75000)
                .quantity(5)
                .totalPrice(375000)
                .isExecuted(true)
                .isReserved(false)
                .user(testUser)
                .stock(testStock)
                .build();

        int price = 75000;
        int quantity = 5;
        int totalPrice = 375000;

        lenient().when(portfolioRepository.findByUser(testUser)).thenReturn(Optional.of(testPortfolio));
        when(userStockRepository.findByUserAndStock(testUser, testStock)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotEnoughStockException.class, () -> {
            orderService.executeSell(order, testUser, testStock, price, quantity, totalPrice);
        });
    }

    @Test
    void 내_주문_내역_전체_조회_빈_리스트_테스트() {
        // Given
        Long userId = 1L;
        when(orderRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // When
        List<OrderResponse> result = orderService.getMyAllOrders(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(orderRepository, times(1)).findByUserId(userId);
    }

    @Test
    void 내_일반_주문_내역_조회_빈_리스트_테스트() {
        // Given
        Long userId = 1L;
        when(orderRepository.findNormalOrdersByUser(userId)).thenReturn(Collections.emptyList());

        // When
        List<OrderResponse> result = orderService.getNormalOrders(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(orderRepository, times(1)).findNormalOrdersByUser(userId);
    }

    @Test
    void 내_예약_주문_내역_조회_빈_리스트_테스트() {
        // Given
        Long userId = 1L;
        when(orderRepository.findReservationOrdersByUser(userId)).thenReturn(Collections.emptyList());

        // When
        List<OrderResponse> result = orderService.getReservationOrders(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(orderRepository, times(1)).findReservationOrdersByUser(userId);
    }
}
