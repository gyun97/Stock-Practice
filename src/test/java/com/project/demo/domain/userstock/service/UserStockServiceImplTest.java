package com.project.demo.domain.userstock.service;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.stock.repository.StockRepository;
import com.project.demo.domain.stock.service.StockService;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.userstock.dto.response.UserStockResponse;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserStockServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class UserStockServiceImplTest {

    @Mock
    private UserStockRepository userStockRepository;

    @Mock
    private StockService stockService;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private UserStockServiceImpl userStockService;

    private User testUser;
    private Stock testStock;
    private UserStock userStock1;
    private UserStock userStock2;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("테스트 사용자")
                .userRole(UserRole.ROLE_USER)
                .build();

        testStock = Stock.builder()
                .id(1L)
                .ticker("005930")
                .name("삼성전자")
                .build();

        userStock1 = UserStock.builder()
                .id(1L)
                .ticker("005930")
                .avgPrice(70000)
                .totalQuantity(10)
                .user(testUser)
                .stock(testStock)
                .userName("테스트 사용자")
                .stockName("삼성전자")
                .build();

        userStock2 = UserStock.builder()
                .id(2L)
                .ticker("000660")
                .avgPrice(150000)
                .totalQuantity(5)
                .user(testUser)
                .stock(testStock)
                .userName("테스트 사용자")
                .stockName("SK하이닉스")
                .build();
    }

    @Test
    void 사용자_보유_주식_조회_성공_테스트() {
        // Given
        Long userId = 1L;
        when(userStockRepository.findByUserId(userId)).thenReturn(Arrays.asList(userStock1, userStock2));
        when(stockService.getCurrentPrice("005930")).thenReturn(75000);
        when(stockService.getCurrentPrice("000660")).thenReturn(155000);
        when(stockRepository.findNameByTicker("005930")).thenReturn("삼성전자");
        when(stockRepository.findNameByTicker("000660")).thenReturn("SK하이닉스");

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("005930", result.get(0).getTicker());
        assertEquals("삼성전자", result.get(0).getCompanyName());
        assertEquals(10, result.get(0).getTotalQuantity());
        assertEquals(70000, result.get(0).getAvgPrice());
        assertEquals(75000, result.get(0).getCurrentPrice());

        verify(userStockRepository, times(1)).findByUserId(userId);
        verify(stockService, times(1)).getCurrentPrice("005930");
        verify(stockService, times(1)).getCurrentPrice("000660");
    }

    @Test
    void 보유_주식_없을_때_테스트() {
        // Given
        Long userId = 1L;
        when(userStockRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userStockRepository, times(1)).findByUserId(userId);
        verify(stockService, never()).getCurrentPrice(anyString());
    }

    @Test
    void 주가_조회_실패시_null_필터링_테스트() {
        // Given
        Long userId = 1L;
        when(userStockRepository.findByUserId(userId)).thenReturn(Arrays.asList(userStock1));
        when(stockService.getCurrentPrice("005930")).thenThrow(new RuntimeException("주가 조회 실패"));
        // 예외 발생 시 getCompanyNameByTicker는 호출되지 않음

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty()); // 예외 발생 시 null이 반환되고 필터링됨
        verify(userStockRepository, times(1)).findByUserId(userId);
        verify(stockService, times(1)).getCurrentPrice("005930");
        verify(stockRepository, never()).findNameByTicker(anyString());
    }

    @Test
    void 회사명_조회_실패시_티커_반환_테스트() {
        // Given
        Long userId = 1L;
        when(userStockRepository.findByUserId(userId)).thenReturn(Arrays.asList(userStock1));
        when(stockService.getCurrentPrice("005930")).thenReturn(75000);
        when(stockRepository.findNameByTicker("005930")).thenThrow(new RuntimeException("회사명 조회 실패"));

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("005930", result.get(0).getCompanyName()); // 오류 시 티커 반환
        verify(stockRepository, times(1)).findNameByTicker("005930");
    }

    @Test
    void 사용자_보유_주식_조회_JSON_파싱_실패_필터링_테스트() {
        // Given
        Long userId = 1L;
        when(userStockRepository.findByUserId(userId)).thenReturn(Arrays.asList(userStock1));
        when(stockService.getCurrentPrice("005930")).thenReturn(75000);
        when(stockRepository.findNameByTicker("005930")).thenThrow(new RuntimeException("회사명 조회 실패"));

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("005930", result.get(0).getCompanyName()); // 오류 시 티커 반환
    }

    @Test
    void 사용자_보유_주식_조회_회사명_null_처리_테스트() {
        // Given
        Long userId = 1L;
        when(userStockRepository.findByUserId(userId)).thenReturn(Arrays.asList(userStock1));
        when(stockService.getCurrentPrice("005930")).thenReturn(75000);
        when(stockRepository.findNameByTicker("005930")).thenReturn(null);

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("005930", result.get(0).getCompanyName()); // null일 때 티커 반환
    }

    @Test
    void 사용자_보유_주식_조회_다수_종목_테스트() {
        // Given
        Long userId = 1L;
        UserStock userStock3 = UserStock.builder()
                .id(3L)
                .ticker("035720")
                .avgPrice(50000)
                .totalQuantity(20)
                .user(testUser)
                .stock(testStock)
                .userName("테스트 사용자")
                .stockName("카카오")
                .build();

        when(userStockRepository.findByUserId(userId))
                .thenReturn(Arrays.asList(userStock1, userStock2, userStock3));
        when(stockService.getCurrentPrice("005930")).thenReturn(75000);
        when(stockService.getCurrentPrice("000660")).thenReturn(155000);
        when(stockService.getCurrentPrice("035720")).thenReturn(55000);
        when(stockRepository.findNameByTicker("005930")).thenReturn("삼성전자");
        when(stockRepository.findNameByTicker("000660")).thenReturn("SK하이닉스");
        when(stockRepository.findNameByTicker("035720")).thenReturn("카카오");

        // When
        List<UserStockResponse> result = userStockService.getUserStocksByUserId(userId);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        verify(stockService, times(1)).getCurrentPrice("005930");
        verify(stockService, times(1)).getCurrentPrice("000660");
        verify(stockService, times(1)).getCurrentPrice("035720");
    }
}

