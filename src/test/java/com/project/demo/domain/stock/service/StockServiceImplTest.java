package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.project.demo.common.kis.KisApiAccessTokenService;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StockServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private KisApiAccessTokenService kisApiAccessTokenService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private StockServiceImpl stockService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stockService, "appKey", "test-app-key");
        ReflectionTestUtils.setField(stockService, "appSecret", "test-app-secret");
        ReflectionTestUtils.setField(stockService, "baseUrl", "https://test-api.com");
    }

    @Test
    void 전체_주식_정보_조회_성공_테스트() throws Exception {
        // Given
        Set<String> stockCodes = new LinkedHashSet<>(Arrays.asList("005930", "000660"));
        String stockJson1 = "{\"ticker\":\"005930\",\"companyName\":\"삼성전자\",\"price\":70000}";
        String stockJson2 = "{\"ticker\":\"000660\",\"companyName\":\"SK하이닉스\",\"price\":150000}";

        StockResponse stock1 = StockResponse.builder()
                .ticker("005930")
                .companyName("삼성전자")
                .price(70000)
                .build();

        StockResponse stock2 = StockResponse.builder()
                .ticker("000660")
                .companyName("SK하이닉스")
                .price(150000)
                .build();

        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = 
                mock(org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("stock:rank:volume", 0, -1)).thenReturn(stockCodes);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:005930")).thenReturn(stockJson1);
        when(valueOps.get("stock:data:000660")).thenReturn(stockJson2);
        when(objectMapper.readValue(stockJson1, StockResponse.class)).thenReturn(stock1);
        when(objectMapper.readValue(stockJson2, StockResponse.class)).thenReturn(stock2);

        // When
        List<StockResponse> result = stockService.showAllStock();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("005930", result.get(0).getTicker());
        assertEquals("000660", result.get(1).getTicker());
    }

    @Test
    void 전체_주식_정보_조회_빈_리스트_테스트() {
        // Given
        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = 
                mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("stock:rank:volume", 0, -1)).thenReturn(Collections.emptySet());

        // When
        List<StockResponse> result = stockService.showAllStock();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void 전체_주식_정보_조회_JSON_파싱_실패_테스트() throws Exception {
        // Given
        Set<String> stockCodes = new LinkedHashSet<>(Arrays.asList("005930"));
        String invalidJson = "invalid-json";

        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = 
                mock(org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("stock:rank:volume", 0, -1)).thenReturn(stockCodes);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:005930")).thenReturn(invalidJson);
        when(objectMapper.readValue(invalidJson, StockResponse.class))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("파싱 실패") {});

        // When
        List<StockResponse> result = stockService.showAllStock();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty()); // 파싱 실패 시 빈 리스트 반환
    }

    @Test
    void 현재_주가_조회_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String json = "{\"price\":70000}";
        JsonNode jsonNode = mock(JsonNode.class);

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn(json);
        when(objectMapper.readTree(json)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.asInt()).thenReturn(70000);

        // When
        int result = stockService.getCurrentPrice(ticker);

        // Then
        assertEquals(70000, result);
        verify(valueOps, times(1)).get("stock:data:" + ticker);
    }

    @Test
    void 현재_주가_조회_데이터_없음_테스트() {
        // Given
        String ticker = "005930";
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn(null);

        // When
        int result = stockService.getCurrentPrice(ticker);

        // Then
        assertEquals(0, result); // 데이터 없을 때 0 반환
    }

    @Test
    void 현재_주가_조회_JSON_파싱_실패_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String invalidJson = "invalid-json";
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn(invalidJson);
        when(objectMapper.readTree(invalidJson))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("파싱 실패") {});

        // When
        int result = stockService.getCurrentPrice(ticker);

        // Then
        assertEquals(0, result); // 예외 발생 시 0 반환
    }

    @Test
    void Access_Token_가져오기_테스트() {
        // Given
        String accessToken = "test-access-token";
        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);

        // When
        String result = stockService.getAccessToken();

        // Then
        assertEquals(accessToken, result);
        verify(kisApiAccessTokenService, times(1)).getAccessToken();
    }

    @Test
    void 전체_주식_정보_조회_null_데이터_필터링_테스트() {
        // Given
        Set<String> stockCodes = new LinkedHashSet<>(Arrays.asList("005930", "000660"));
        String stockJson1 = "{\"ticker\":\"005930\",\"companyName\":\"삼성전자\",\"price\":70000}";
        String stockJson2 = null; // null 데이터

        StockResponse stock1 = StockResponse.builder()
                .ticker("005930")
                .companyName("삼성전자")
                .price(70000)
                .build();

        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = 
                mock(org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("stock:rank:volume", 0, -1)).thenReturn(stockCodes);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:005930")).thenReturn(stockJson1);
        when(valueOps.get("stock:data:000660")).thenReturn(stockJson2);
        try {
            when(objectMapper.readValue(stockJson1, StockResponse.class)).thenReturn(stock1);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ignore
        }

        // When
        List<StockResponse> result = stockService.showAllStock();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size()); // null 데이터는 필터링됨
        assertEquals("005930", result.get(0).getTicker());
    }

    @Test
    void 전체_주식_정보_조회_빈_JSON_필터링_테스트() {
        // Given
        Set<String> stockCodes = new LinkedHashSet<>(Arrays.asList("005930"));
        String emptyJson = ""; // 빈 문자열

        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps = 
                mock(org.springframework.data.redis.core.ZSetOperations.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("stock:rank:volume", 0, -1)).thenReturn(stockCodes);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:005930")).thenReturn(emptyJson);

        // When
        List<StockResponse> result = stockService.showAllStock();

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty()); // 빈 JSON은 필터링됨
    }

    @Test
    void 분봉_조회_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String date = "20241023";
        String time = "090000";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode node1 = mock(JsonNode.class);
        JsonNode node2 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1, node2).iterator());

        JsonNode timeNode1 = mock(JsonNode.class);
        JsonNode priceNode1 = mock(JsonNode.class);
        JsonNode timeNode2 = mock(JsonNode.class);
        JsonNode priceNode2 = mock(JsonNode.class);

        when(node1.get("stck_cntg_hour")).thenReturn(timeNode1);
        when(timeNode1.asText()).thenReturn("090000");
        when(node1.get("stck_prpr")).thenReturn(priceNode1);
        when(priceNode1.asInt()).thenReturn(70000);

        when(node2.get("stck_cntg_hour")).thenReturn(timeNode2);
        when(timeNode2.asText()).thenReturn("090100");
        when(node2.get("stck_prpr")).thenReturn(priceNode2);
        when(priceNode2.asInt()).thenReturn(70100);

        // When
        List<StockResponse> result = stockService.getMinuteCandles(ticker, date, time);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("090000", result.get(0).getTradeTime());
        assertEquals(70000, result.get(0).getPrice());
        assertEquals("090100", result.get(1).getTradeTime());
        assertEquals(70100, result.get(1).getPrice());
    }

    @Test
    void 분봉_조회_output2_없음_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String date = "20241023";
        String time = "090000";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(false);

        // When
        List<StockResponse> result = stockService.getMinuteCandles(ticker, date, time);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void 기간별_주식_정보_조회_일_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "D";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode dateNode = mock(JsonNode.class);
        JsonNode openNode = mock(JsonNode.class);
        JsonNode highNode = mock(JsonNode.class);
        JsonNode lowNode = mock(JsonNode.class);
        JsonNode closeNode = mock(JsonNode.class);
        JsonNode volumeNode = mock(JsonNode.class);
        JsonNode node1 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1).iterator());

        when(node1.get("stck_bsop_date")).thenReturn(dateNode);
        when(dateNode.asText()).thenReturn("20241023");
        when(node1.get("stck_oprc")).thenReturn(openNode);
        when(openNode.asInt()).thenReturn(70000);
        when(node1.get("stck_hgpr")).thenReturn(highNode);
        when(highNode.asInt()).thenReturn(71000);
        when(node1.get("stck_lwpr")).thenReturn(lowNode);
        when(lowNode.asInt()).thenReturn(69000);
        when(node1.get("stck_clpr")).thenReturn(closeNode);
        when(closeNode.asInt()).thenReturn(70500);
        when(node1.get("acml_vol")).thenReturn(volumeNode);
        when(volumeNode.asLong()).thenReturn(1000000L);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfo(ticker, period);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("20241023", result.get(0).getDate());
        assertEquals(70000, result.get(0).getOpen());
        assertEquals(71000, result.get(0).getHigh());
        assertEquals(69000, result.get(0).getLow());
        assertEquals(70500, result.get(0).getClose());
        assertEquals(1000000L, result.get(0).getVolume());
    }

    @Test
    void 기간별_주식_정보_조회_주_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "W";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode node1 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1).iterator());

        JsonNode dateNode = mock(JsonNode.class);
        JsonNode openNode = mock(JsonNode.class);
        JsonNode highNode = mock(JsonNode.class);
        JsonNode lowNode = mock(JsonNode.class);
        JsonNode closeNode = mock(JsonNode.class);
        JsonNode volumeNode = mock(JsonNode.class);

        when(node1.get("stck_bsop_date")).thenReturn(dateNode);
        when(dateNode.asText()).thenReturn("20241023");
        when(node1.get("stck_oprc")).thenReturn(openNode);
        when(openNode.asInt()).thenReturn(70000);
        when(node1.get("stck_hgpr")).thenReturn(highNode);
        when(highNode.asInt()).thenReturn(71000);
        when(node1.get("stck_lwpr")).thenReturn(lowNode);
        when(lowNode.asInt()).thenReturn(69000);
        when(node1.get("stck_clpr")).thenReturn(closeNode);
        when(closeNode.asInt()).thenReturn(70500);
        when(node1.get("acml_vol")).thenReturn(volumeNode);
        when(volumeNode.asLong()).thenReturn(1000000L);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfo(ticker, period);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void 기간별_주식_정보_조회_월_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "M";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode node1 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1).iterator());

        JsonNode dateNode = mock(JsonNode.class);
        JsonNode openNode = mock(JsonNode.class);
        JsonNode highNode = mock(JsonNode.class);
        JsonNode lowNode = mock(JsonNode.class);
        JsonNode closeNode = mock(JsonNode.class);
        JsonNode volumeNode = mock(JsonNode.class);

        when(node1.get("stck_bsop_date")).thenReturn(dateNode);
        when(dateNode.asText()).thenReturn("20241023");
        when(node1.get("stck_oprc")).thenReturn(openNode);
        when(openNode.asInt()).thenReturn(70000);
        when(node1.get("stck_hgpr")).thenReturn(highNode);
        when(highNode.asInt()).thenReturn(71000);
        when(node1.get("stck_lwpr")).thenReturn(lowNode);
        when(lowNode.asInt()).thenReturn(69000);
        when(node1.get("stck_clpr")).thenReturn(closeNode);
        when(closeNode.asInt()).thenReturn(70500);
        when(node1.get("acml_vol")).thenReturn(volumeNode);
        when(volumeNode.asLong()).thenReturn(1000000L);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfo(ticker, period);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void 기간별_주식_정보_조회_연_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "Y";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode node1 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1).iterator());

        JsonNode dateNode = mock(JsonNode.class);
        JsonNode openNode = mock(JsonNode.class);
        JsonNode highNode = mock(JsonNode.class);
        JsonNode lowNode = mock(JsonNode.class);
        JsonNode closeNode = mock(JsonNode.class);
        JsonNode volumeNode = mock(JsonNode.class);

        when(node1.get("stck_bsop_date")).thenReturn(dateNode);
        when(dateNode.asText()).thenReturn("20241023");
        when(node1.get("stck_oprc")).thenReturn(openNode);
        when(openNode.asInt()).thenReturn(70000);
        when(node1.get("stck_hgpr")).thenReturn(highNode);
        when(highNode.asInt()).thenReturn(71000);
        when(node1.get("stck_lwpr")).thenReturn(lowNode);
        when(lowNode.asInt()).thenReturn(69000);
        when(node1.get("stck_clpr")).thenReturn(closeNode);
        when(closeNode.asInt()).thenReturn(70500);
        when(node1.get("acml_vol")).thenReturn(volumeNode);
        when(volumeNode.asLong()).thenReturn(1000000L);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfo(ticker, period);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void 기간별_주식_정보_조회_기본값_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "INVALID";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode node1 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1).iterator());

        JsonNode dateNode = mock(JsonNode.class);
        JsonNode openNode = mock(JsonNode.class);
        JsonNode highNode = mock(JsonNode.class);
        JsonNode lowNode = mock(JsonNode.class);
        JsonNode closeNode = mock(JsonNode.class);
        JsonNode volumeNode = mock(JsonNode.class);

        when(node1.get("stck_bsop_date")).thenReturn(dateNode);
        when(dateNode.asText()).thenReturn("20241023");
        when(node1.get("stck_oprc")).thenReturn(openNode);
        when(openNode.asInt()).thenReturn(70000);
        when(node1.get("stck_hgpr")).thenReturn(highNode);
        when(highNode.asInt()).thenReturn(71000);
        when(node1.get("stck_lwpr")).thenReturn(lowNode);
        when(lowNode.asInt()).thenReturn(69000);
        when(node1.get("stck_clpr")).thenReturn(closeNode);
        when(closeNode.asInt()).thenReturn(70500);
        when(node1.get("acml_vol")).thenReturn(volumeNode);
        when(volumeNode.asLong()).thenReturn(1000000L);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfo(ticker, period);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void 기간별_주식_정보_조회_output2_없음_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "D";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(false);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfo(ticker, period);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void 기간별_주식_정보_범위_조회_성공_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "D";
        String startDate = "20241001";
        String endDate = "20241023";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);
        ArrayNode output2Array = mock(ArrayNode.class);
        JsonNode node1 = mock(JsonNode.class);
        JsonNode node2 = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(true);
        when(jsonNode.get("output2")).thenReturn(output2Array);
        when(output2Array.iterator()).thenReturn(Arrays.asList(node1, node2).iterator());

        JsonNode dateNode1 = mock(JsonNode.class);
        JsonNode openNode1 = mock(JsonNode.class);
        JsonNode highNode1 = mock(JsonNode.class);
        JsonNode lowNode1 = mock(JsonNode.class);
        JsonNode closeNode1 = mock(JsonNode.class);
        JsonNode volumeNode1 = mock(JsonNode.class);
        JsonNode dateNode2 = mock(JsonNode.class);
        JsonNode openNode2 = mock(JsonNode.class);
        JsonNode highNode2 = mock(JsonNode.class);
        JsonNode lowNode2 = mock(JsonNode.class);
        JsonNode closeNode2 = mock(JsonNode.class);
        JsonNode volumeNode2 = mock(JsonNode.class);

        when(node1.get("stck_bsop_date")).thenReturn(dateNode1);
        when(dateNode1.asText()).thenReturn("20241001");
        when(node1.get("stck_oprc")).thenReturn(openNode1);
        when(openNode1.asInt()).thenReturn(70000);
        when(node1.get("stck_hgpr")).thenReturn(highNode1);
        when(highNode1.asInt()).thenReturn(71000);
        when(node1.get("stck_lwpr")).thenReturn(lowNode1);
        when(lowNode1.asInt()).thenReturn(69000);
        when(node1.get("stck_clpr")).thenReturn(closeNode1);
        when(closeNode1.asInt()).thenReturn(70500);
        when(node1.get("acml_vol")).thenReturn(volumeNode1);
        when(volumeNode1.asLong()).thenReturn(1000000L);

        when(node2.get("stck_bsop_date")).thenReturn(dateNode2);
        when(dateNode2.asText()).thenReturn("20241023");
        when(node2.get("stck_oprc")).thenReturn(openNode2);
        when(openNode2.asInt()).thenReturn(71000);
        when(node2.get("stck_hgpr")).thenReturn(highNode2);
        when(highNode2.asInt()).thenReturn(72000);
        when(node2.get("stck_lwpr")).thenReturn(lowNode2);
        when(lowNode2.asInt()).thenReturn(70000);
        when(node2.get("stck_clpr")).thenReturn(closeNode2);
        when(closeNode2.asInt()).thenReturn(71500);
        when(node2.get("acml_vol")).thenReturn(volumeNode2);
        when(volumeNode2.asLong()).thenReturn(2000000L);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfoByRange(ticker, period, startDate, endDate);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("20241001", result.get(0).getDate());
        assertEquals("20241023", result.get(1).getDate());
    }

    @Test
    void 기간별_주식_정보_범위_조회_output2_없음_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "D";
        String startDate = "20241001";
        String endDate = "20241023";
        String accessToken = "test-token";

        JsonNode jsonNode = mock(JsonNode.class);

        when(kisApiAccessTokenService.getAccessToken()).thenReturn(accessToken);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(jsonNode));
        when(jsonNode.has("output2")).thenReturn(false);

        // When
        List<CandleResponse> result = stockService.getPeriodStockInfoByRange(ticker, period, startDate, endDate);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void 현재_주가_조회_price_필드_없음_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String json = "{\"ticker\":\"005930\"}";
        JsonNode jsonNode = mock(JsonNode.class);

        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn(json);
        when(objectMapper.readTree(json)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(null);

        // When
        int result = stockService.getCurrentPrice(ticker);

        // Then
        assertEquals(0, result); // price 필드가 없으면 0 반환
    }

    @Test
    void 현재_주가_조회_빈_문자열_테스트() {
        // Given
        String ticker = "005930";
        String emptyJson = "";
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = 
                mock(org.springframework.data.redis.core.ValueOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("stock:data:" + ticker)).thenReturn(emptyJson);

        // When
        int result = stockService.getCurrentPrice(ticker);

        // Then
        assertEquals(0, result); // 빈 문자열이면 0 반환
    }
}

