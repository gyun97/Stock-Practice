package com.project.demo.domain.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.common.websocket.WebSocketSessionManager;
import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.dto.response.RankingResponse;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.userstock.service.UserStockService;
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
 * PortfolioServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserStockService userStockService;

    @Mock
    private WebSocketSessionManager sessionManager;

    @InjectMocks
    private PortfolioServiceImpl portfolioService;

    private User testUser;
    private Portfolio testPortfolio;

    @BeforeEach
    void setUp() {
        testUser = User.createNewUser(
                "test@example.com",
                "테스트 사용자",
                "password",
                UserRole.ROLE_USER,
                SocialType.LOCAL,
                "");
        ReflectionTestUtils.setField(testUser, "id", 1L);

        testPortfolio = Portfolio.builder()
                .balance(10000000)
                .totalAsset(10000000)
                .totalQuantity(0)
                .holdCount(0)
                .stockAsset(0)
                .user(testUser)
                .build();
        ReflectionTestUtils.setField(testPortfolio, "id", 1L);
    }

    @Test
    void 포트폴리오_조회_캐시_히트_테스트() throws Exception {
        // Given
        Long userId = 1L;
        String cacheKey = "portfolio:data:" + userId;
        String cachedJson = "{\"stockAsset\":5000000,\"totalAsset\":15000000,\"returnRate\":50.0}";
        Map<String, Object> cachedData = new HashMap<>();
        cachedData.put("stockAsset", 5000000L);
        cachedData.put("totalAsset", 15000000L);
        cachedData.put("returnRate", 50.0);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().get(cacheKey)).thenReturn(cachedJson);
        when(objectMapper.readValue(cachedJson, Map.class)).thenReturn(cachedData);

        // When
        PortfolioResponse response = portfolioService.getMyPortfolio(userId);

        // Then
        assertNotNull(response);
        verify(redisTemplate.opsForValue(), atLeastOnce()).get(cacheKey);
    }

    @Test
    void 포트폴리오_조회_캐시_미스_테스트() {
        // Given
        Long userId = 1L;
        String cacheKey = "portfolio:data:" + userId;
        ReflectionTestUtils.setField(testPortfolio, "userStocks", new ArrayList<>());

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = mock(
                org.springframework.data.redis.core.ZSetOperations.class);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(null);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // calculateAndCacheReturnRate 내부 로직 Mock
        // userStocks가 비어있으므로 getStockPrice는 호출되지 않음
        // hasChanged가 true일 때만 set과 writeValueAsString이 호출됨
        // 캐시가 없으므로 항상 hasChanged는 true
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any(java.util.concurrent.TimeUnit.class));
        try {
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"test\":\"data\"}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }

        // When
        PortfolioResponse response = portfolioService.getMyPortfolio(userId);

        // Then
        assertNotNull(response);
        verify(valueOps, atLeastOnce()).get(cacheKey);
    }

    @Test
    void 포트폴리오_조회_포트폴리오_없음_예외_테스트() {
        // Given
        Long userId = 1L;
        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundPortfolioException.class, () -> {
            portfolioService.getMyPortfolio(userId);
        });
    }

    @Test
    void 랭킹_조회_테스트() {
        // Given
        int limit = 10;
        Set<String> topUserIds = new LinkedHashSet<>(Arrays.asList("1"));
        ReflectionTestUtils.setField(testPortfolio, "userStocks", new ArrayList<>());

        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = mock(
                org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("user:rank:totalAsset", 0, (limit * 2) - 1)).thenReturn(topUserIds);
        when(portfolioRepository.findByUserId(1L)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null); // 캐시 없음
        when(zSetOps.score(anyString(), anyString())).thenReturn(15000000.0);
        lenient().when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        // set 메서드 Mock (캐시 저장) - 호출되지 않을 수 있음
        lenient().doNothing().when(valueOps).set(anyString(), anyString(), anyLong(),
                any(java.util.concurrent.TimeUnit.class));
        // objectMapper Mock - 호출되지 않을 수 있음
        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"data\"}");
        } catch (Exception e) {
            // ignore
        }

        // When
        List<RankingResponse> rankings = portfolioService.getRanking(limit);

        // Then
        assertNotNull(rankings);
        verify(zSetOps, atLeastOnce()).reverseRange("user:rank:totalAsset", 0, (limit * 2) - 1);
    }

    @Test
    void 랭킹_조회_빈_결과_테스트() {
        // Given
        int limit = 10;
        when(redisTemplate.opsForZSet()).thenReturn(mock(org.springframework.data.redis.core.ZSetOperations.class));
        when(redisTemplate.opsForZSet().reverseRange("user:rank:totalAsset", 0, (limit * 2) - 1))
                .thenReturn(Collections.emptySet());
        when(portfolioRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<RankingResponse> rankings = portfolioService.getRanking(limit);

        // Then
        assertNotNull(rankings);
        assertTrue(rankings.isEmpty());
    }

    @Test
    void 포트폴리오_조회_캐시_파싱_실패_테스트() throws Exception {
        // Given
        Long userId = 1L;
        String cacheKey = "portfolio:data:" + userId;
        String invalidJson = "invalid-json";
        ReflectionTestUtils.setField(testPortfolio, "userStocks", new ArrayList<>());

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = mock(
                org.springframework.data.redis.core.ZSetOperations.class);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(invalidJson);
        when(objectMapper.readValue(invalidJson, Map.class))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("파싱 실패") {
                });
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any(java.util.concurrent.TimeUnit.class));
        try {
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"test\":\"data\"}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }

        // When
        PortfolioResponse response = portfolioService.getMyPortfolio(userId);

        // Then
        assertNotNull(response);
        verify(valueOps, atLeastOnce()).get(cacheKey);
    }

    @Test
    void 랭킹_조회_다수_사용자_테스트() {
        // Given
        int limit = 10;
        Set<String> topUserIds = new LinkedHashSet<>(Arrays.asList("1", "2", "3"));
        ReflectionTestUtils.setField(testPortfolio, "userStocks", new ArrayList<>());

        Portfolio portfolio2 = Portfolio.builder()
                .balance(15000000)
                .totalAsset(15000000)
                .totalQuantity(0)
                .holdCount(0)
                .stockAsset(0)
                .user(testUser)
                .build();
        ReflectionTestUtils.setField(portfolio2, "id", 2L);

        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = mock(
                org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("user:rank:totalAsset", 0, (limit * 2) - 1)).thenReturn(topUserIds);
        when(portfolioRepository.findByUserId(1L)).thenReturn(Optional.of(testPortfolio));
        when(portfolioRepository.findByUserId(2L)).thenReturn(Optional.of(portfolio2));
        when(portfolioRepository.findByUserId(3L)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(zSetOps.score(anyString(), anyString())).thenReturn(15000000.0);
        lenient().when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        lenient().doNothing().when(valueOps).set(anyString(), anyString(), anyLong(),
                any(java.util.concurrent.TimeUnit.class));
        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"data\"}");
        } catch (Exception e) {
            // ignore
        }

        // When
        List<RankingResponse> rankings = portfolioService.getRanking(limit);

        // Then
        assertNotNull(rankings);
        assertTrue(rankings.size() <= 3);
        verify(zSetOps, atLeastOnce()).reverseRange("user:rank:totalAsset", 0, (limit * 2) - 1);
    }

    @Test
    void 포트폴리오_조회_빈_보유주식_테스트() {
        // Given
        Long userId = 1L;
        String cacheKey = "portfolio:data:" + userId;
        ReflectionTestUtils.setField(testPortfolio, "userStocks", new ArrayList<>());

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);
        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = mock(
                org.springframework.data.redis.core.ZSetOperations.class);

        when(portfolioRepository.findByUserId(userId)).thenReturn(Optional.of(testPortfolio));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(cacheKey)).thenReturn(null);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any(java.util.concurrent.TimeUnit.class));
        try {
            when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"test\":\"data\"}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }

        // When
        PortfolioResponse response = portfolioService.getMyPortfolio(userId);

        // Then
        assertNotNull(response);
        assertEquals(0L, response.getStockAsset()); // 보유 주식 없음
    }

    @Test
    void 랭킹_조회_무효_데이터_필터링_및_클린업_테스트() {
        // Given
        int limit = 2;
        // Redis는 1, 2, 3번 ID를 반환 (limit=2인데 넉넉히 가져온 상황 가정)
        Set<String> topUserIds = new LinkedHashSet<>(Arrays.asList("1", "2", "3"));
        
        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = mock(
                org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(
                org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        // reverseRange는 fetchLimit(limit * 2 = 4) 범위로 호출됨
        when(zSetOps.reverseRange("user:rank:totalAsset", 0, 3)).thenReturn(topUserIds);
        
        // 1번 유저는 유효, 2번 유저는 삭제됨(null), 3번 유저는 유효
        when(portfolioRepository.findByUserId(1L)).thenReturn(Optional.of(testPortfolio));
        when(portfolioRepository.findByUserId(2L)).thenReturn(Optional.empty());
        
        Portfolio portfolio3 = Portfolio.builder()
                .balance(20000000)
                .totalAsset(20000000)
                .user(testUser)
                .build();
        ReflectionTestUtils.setField(portfolio3, "id", 3L);
        when(portfolioRepository.findByUserId(3L)).thenReturn(Optional.of(portfolio3));

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(zSetOps.score(anyString(), anyString())).thenReturn(10000000.0);

        // When
        List<RankingResponse> rankings = portfolioService.getRanking(limit);

        // Then
        assertNotNull(rankings);
        assertEquals(2, rankings.size()); // 2번이 걸러졌으므로 1, 3번이 포함되어 2개가 되어야 함
        assertEquals(1L, rankings.get(0).getUserId());
        assertEquals(3L, rankings.get(1).getUserId());

        // 2번 유저 ID는 Redis에서 삭제되어야 함
        verify(zSetOps, times(1)).remove("user:rank:totalAsset", "2");
    }
}
