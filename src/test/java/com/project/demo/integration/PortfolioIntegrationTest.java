package com.project.demo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.jwt.JwtAuthenticationToken;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.oauth2.SocialType;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.dto.response.StockData;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.enums.Market;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Commit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Portfolio 관련 통합 테스트
 * Testcontainers를 사용하여 실제 MySQL과 Redis 컨테이너로 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestConfig.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PortfolioIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PortfolioRepository portfolioRepository;

        @Autowired
        private StockRepository stockRepository;

        @Autowired
        private UserStockRepository userStockRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ExecutionRepository executionRepository;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private JwtUtil jwtUtil;

        @Autowired
        private StringRedisTemplate redisTemplate;

        @Autowired
        private ObjectMapper objectMapper;

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
                                .email("portfolio@example.com")
                                .password(passwordEncoder.encode("Test123!@#"))
                                .name("포트폴리오 테스트 사용자")
                                .userRole(UserRole.ROLE_USER)
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

                // Redis에 주식 가격 데이터 추가 (삼성전자 005930)
                StockData stockData = StockData.builder()
                                .ticker("005930")
                                .price(70000)
                                .companyName("삼성전자")
                                .volume(1000000L)
                                .build();
                try {
                        redisTemplate.opsForValue().set("stock:data:005930", objectMapper.writeValueAsString(stockData));
                } catch (Exception e) {
                        e.printStackTrace();
                }

                // 인증 설정
                authUser = new AuthUser(testUser.getId(), testUser.getEmail(), testUser.getUserRole(),
                                testUser.getName());
                JwtAuthenticationToken authentication = new JwtAuthenticationToken(authUser);
                SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        @Transactional
        @Commit
        void cleanupDatabase() {
                // Redis 초기화 추가
                redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
                
                // Native SQL을 사용하여 더 확실하게 삭제
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
                executionRepository.deleteAll();
                orderRepository.deleteAll();
                userStockRepository.deleteAll();
                portfolioRepository.deleteAll();
                refreshTokenRepository.deleteAll();
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
        void 포트폴리오_조회_테스트() throws Exception {
                // When & Then
                String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(),
                                testUser.getUserRole(), testUser.getName());
                mockMvc.perform(get("/api/v1/portfolios/users/{userId}", testUser.getId())
                                .header("Authorization", accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.balance").exists())
                                .andExpect(jsonPath("$.data.totalAsset").exists());
        }

        @Test
        void 주식_매수_후_포트폴리오_업데이트_테스트() throws Exception {
                // Given - 주식 매수
                String ticker = "005930";
                int quantity = 10;

                String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(),
                                testUser.getUserRole(), testUser.getName());
                mockMvc.perform(post("/api/v1/orders/buying/{ticker}", ticker)
                                .param("quantity", String.valueOf(quantity))
                                .header("Authorization", accessToken))
                                .andExpect(status().isOk());

                // When - 포트폴리오 조회
                mockMvc.perform(get("/api/v1/portfolios/users/{userId}", testUser.getId())
                                .header("Authorization", accessToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.holdCount").value(1))
                                .andExpect(jsonPath("$.data.totalQuantity").value(quantity));

                // Then - 데이터베이스 확인
                Portfolio updatedPortfolio = portfolioRepository.findByUser(testUser).orElseThrow();
                assertEquals(1, updatedPortfolio.getHoldCount());
                assertEquals(quantity, updatedPortfolio.getTotalQuantity());
                assertTrue(updatedPortfolio.getStockAsset() > 0);
        }

        @Test
        void 랭킹_조회_테스트() throws Exception {
                // Given - 여러 사용자 생성
                User user1 = User.builder()
                                .email("user1@example.com")
                                .password(passwordEncoder.encode("Test123!@#"))
                                .name("사용자1")
                                .userRole(UserRole.ROLE_USER)
                                .socialType(SocialType.LOCAL)
                                .isDeleted(false)
                                .build();
                user1 = userRepository.save(user1);

                Portfolio portfolio1 = Portfolio.builder()
                                .balance(5000000L)
                                .totalAsset(20000000L)
                                .totalQuantity(0)
                                .stockAsset(15000000)
                                .holdCount(0)
                                .user(user1)
                                .build();
                portfolioRepository.save(portfolio1);

                User user2 = User.builder()
                                .email("user2@example.com")
                                .password(passwordEncoder.encode("Test123!@#"))
                                .name("사용자2")
                                .userRole(UserRole.ROLE_USER)
                                .socialType(SocialType.LOCAL)
                                .isDeleted(false)
                                .build();
                user2 = userRepository.save(user2);

                Portfolio portfolio2 = Portfolio.builder()
                                .balance(5000000L)
                                .totalAsset(15000000L)
                                .totalQuantity(0)
                                .stockAsset(10000000)
                                .holdCount(0)
                                .user(user2)
                                .build();
                portfolioRepository.save(portfolio2);
                
                entityManager.flush();
                entityManager.clear();

                // When & Then
                String accessToken = jwtUtil.createAccessToken(testUser.getId(), testUser.getEmail(),
                                testUser.getUserRole(), testUser.getName());
                mockMvc.perform(get("/api/v1/portfolios/ranking")
                                .param("limit", "10")
                                .header("Authorization", accessToken))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data.length()").value(greaterThanOrEqualTo(2)));
        }
}
