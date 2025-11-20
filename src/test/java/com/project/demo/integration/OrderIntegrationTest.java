package com.project.demo.integration;

import com.project.demo.common.jwt.JwtAuthenticationToken;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.oauth2.SocialType;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.enums.Market;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Commit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order 관련 통합 테스트
 * Testcontainers를 사용하여 실제 MySQL과 Redis 컨테이너로 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
@DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS) // 테스트 간의 격리를 보장(스프링 테스트 컨텍스트는 테스트 속도 향상을 위해 컨텍스트를 캐싱하여 재활용을 무효화)
@Transactional
class OrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserStockRepository userStockRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JwtUtil jwtUtil;

    @PersistenceContext
    private EntityManager entityManager;

    private User testUser;
    private Stock testStock;
    private Portfolio testPortfolio;
    private AuthUser authUser;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        cleanupDatabase();

        // 사용자 생성
        testUser = User.builder()
                .email("order@example.com")
                .password(passwordEncoder.encode("Test123!@#"))
                .name("주문 테스트 사용자")
                .userRole(UserRole.ROLE_USER)
                .balance(10000000L)
                .socialType(SocialType.LOCAL)
                .isDeleted(false)
                .build();
        testUser = userRepository.save(testUser);

        // 주식 생성
        testStock = Stock.builder()
                .ticker("005930")
                .name("삼성전자")
                .market(Market.KOSPI)
                .volume(1000000L)
                .build();
        testStock = stockRepository.save(testStock);

        // 포트폴리오 생성
        testPortfolio = Portfolio.builder()
                .balance(10000000L)
                .totalAsset(10000000L)
                .totalQuantity(0)
                .stockAsset(0)
                .holdCount(0)
                .user(testUser)
                .build();
        testPortfolio = portfolioRepository.save(testPortfolio);

        // 인증 설정
        authUser = new AuthUser(testUser.getId(), testUser.getEmail(), testUser.getUserRole(), testUser.getName());
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(authUser);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Transactional
    @Commit
    void cleanupDatabase() {
        // Native SQL을 사용하여 더 확실하게 삭제
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        executionRepository.deleteAll();
        orderRepository.deleteAll();
        userStockRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();
        stockRepository.deleteAll();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 주식_즉시_매수_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 10;

        // When & Then
        String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(), testUser.getUserRole(), testUser.getName());
        mockMvc.perform(post("/api/v1/orders/buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity))
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        // 데이터베이스 확인
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        Portfolio updatedPortfolio = portfolioRepository.findByUser(updatedUser).orElseThrow();
        Order order = orderRepository.findByUserId(updatedUser.getId())
                .stream()
                .filter(o -> o.getStock().getId().equals(testStock.getId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(order);
        assertEquals(OrderType.BUY, order.getType());
        assertEquals(quantity, order.getQuantity());
        assertTrue(order.isExecuted());
        assertFalse(order.isReserved());
    }

    @Test
    void 주식_예약_매수_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 10;
        int targetPrice = 50000;

        // When & Then
        String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(), testUser.getUserRole(), testUser.getName());
        mockMvc.perform(post("/api/v1/orders/reserve-buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity))
                        .param("targetPrice", String.valueOf(targetPrice))
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        // 데이터베이스 확인
        Order order = orderRepository.findByUserId(testUser.getId())
                .stream()
                .filter(o -> o.getStock().getId().equals(testStock.getId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(order);
        assertEquals(OrderType.BUY, order.getType());
        assertEquals(quantity, order.getQuantity());
        assertEquals(targetPrice, order.getPrice());
        assertTrue(order.isReserved());
        assertFalse(order.isExecuted());
    }

    @Test
    void 주식_매수_후_매도_테스트() throws Exception {
        // Given - 먼저 주식 매수
        String ticker = "005930";
        int buyQuantity = 10;

        // 주식 매수
        String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(), testUser.getUserRole(), testUser.getName());
        mockMvc.perform(post("/api/v1/orders/buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(buyQuantity))
                        .header("Authorization", accessToken))
                .andExpect(status().isOk());

        // When - 주식 매도
        int sellQuantity = 5;

        mockMvc.perform(post("/api/v1/orders/selling/{ticker}", ticker)
                        .param("quantity", String.valueOf(sellQuantity))
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());

        // Then - 데이터베이스 확인
        Stock stock = stockRepository.findByTicker(ticker).orElseThrow();
        UserStock userStock = userStockRepository.findByUserAndStock(testUser, stock)
                .orElseThrow();

        assertEquals(buyQuantity - sellQuantity, userStock.getTotalQuantity());
    }

    @Test
    void 내_주문_내역_조회_테스트() throws Exception {
        // Given - 주문 생성
        String ticker = "005930";
        int quantity = 10;

        String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(), testUser.getUserRole(), testUser.getName());

        mockMvc.perform(post("/api/v1/orders/buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity))
                        .header("Authorization", accessToken))
                .andExpect(status().isOk());

        // When & Then
        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void 예약_주문_취소_테스트() throws Exception {
        // Given - 예약 주문 생성
        String ticker = "005930";
        int quantity = 10;
        int targetPrice = 50000;

        String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(), testUser.getUserRole(), testUser.getName());
        mockMvc.perform(post("/api/v1/orders/reserve-buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity))
                        .param("targetPrice", String.valueOf(targetPrice))
                        .header("Authorization", accessToken))
                .andExpect(status().isOk());

        Order reservedOrder = orderRepository.findByUserId(testUser.getId())
                .stream()
                .filter(o -> o.getStock().getId().equals(testStock.getId()))
                .findFirst()
                .orElseThrow();
        Long orderId = reservedOrder.getId();

        // When - 예약 주문 취소
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId)
                        .header("Authorization", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId));

        // Then - 주문이 삭제되었는지 확인
        assertFalse(orderRepository.existsById(orderId));
    }
}

