package com.project.demo.integration;

import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.enums.Market;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.common.oauth2.SocialType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Commit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User testUser;
    private Stock stock1;
    private Stock stock2;

    @BeforeEach
    @Transactional
    @Commit
    void setUp() {
        cleanupDatabase();

        testUser = User.builder()
                .email("concurrency@example.com")
                .name("Concurrency Test")
                .password("password")
                .userRole(UserRole.ROLE_USER)
                .socialType(SocialType.LOCAL)
                .build();
        testUser = userRepository.save(testUser);

        stock1 = Stock.builder()
                .ticker("STOCK1")
                .name("Stock One")
                .market(Market.KOSPI)
                .build();
        stock1 = stockRepository.save(stock1);

        stock2 = Stock.builder()
                .ticker("STOCK2")
                .name("Stock Two")
                .market(Market.KOSPI)
                .build();
        stock2 = stockRepository.save(stock2);

        Portfolio portfolio = Portfolio.builder()
                .balance(10000L) // Set balance to 10,000
                .totalAsset(10000L)
                .user(testUser)
                .build();
        portfolioRepository.save(portfolio);
    }

    @Transactional
    @Commit
    void cleanupDatabase() {
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        orderRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();
        stockRepository.deleteAll();
        entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }

    @Test
    @DisplayName("예약 매수 동시성 테스트: 잔액이 부족한 경우 두 번째 주문은 실패해야 함")
    void reservedOrderConcurrencyTest() throws InterruptedException {
        // Given: 잔액 10,000원인 사용자에게 10,000원짜리 예약 매수 주문 2개 등록
        orderService.reserveBuy(testUser.getId(), "STOCK1", 1, 10000);
        orderService.reserveBuy(testUser.getId(), "STOCK2", 1, 10000);

        // When: 두 주문을 거의 동시에 체결 시도
        // executeReservedOrdersForTicker는 @Async이므로 별도 스레드에서 실행됨
        orderService.executeReservedOrdersForTicker("STOCK1", 10000);
        orderService.executeReservedOrdersForTicker("STOCK2", 10000);

        // 비동기 작업 완료 대기 (잠시 대기)
        Thread.sleep(2000);

        // Then: 잔액은 0원이어야 하며, 하나만 체결되어야 함
        Portfolio portfolio = portfolioRepository.findByUser(testUser).orElseThrow();
        long executedCount = orderRepository.findByUserId(testUser.getId()).stream()
                .filter(Order::isExecuted)
                .count();

        System.out.println("Executed Count: " + executedCount);
        System.out.println("Remaining Balance: " + portfolio.getBalance());

        assertEquals(1, executedCount, "단 하나의 주문만 체결되어야 합니다.");
        assertTrue(portfolio.getBalance() >= 0, "잔액은 0원 이상이어야 합니다.");
    }
}
