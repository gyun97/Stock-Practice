package com.project.demo.domain.stock.controller;

import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;
import com.project.demo.domain.stock.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StockController 단위 테스트
 */
@WebMvcTest(controllers = StockController.class, excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockService stockService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void 전체_주식_정보_조회_API_테스트() throws Exception {
        // Given
        List<StockResponse> responses = Arrays.asList(
                StockResponse.builder()
                        .ticker("005930")
                        .companyName("삼성전자")
                        .price(70000)
                        .changeAmount(1000)
                        .changeRate(1.45)
                        .build(),
                StockResponse.builder()
                        .ticker("000660")
                        .companyName("SK하이닉스")
                        .price(150000)
                        .changeAmount(-2000)
                        .changeRate(-1.32)
                        .build()
        );
        when(stockService.showAllStock()).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ticker").value("005930"))
                .andExpect(jsonPath("$.data[0].companyName").value("삼성전자"));

        verify(stockService, times(1)).showAllStock();
    }

    @Test
    void 기간별_주식_정보_조회_API_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "day";
        List<CandleResponse> responses = Arrays.asList(
                CandleResponse.builder()
                        .date("20241114")
                        .open(70000)
                        .high(71000)
                        .low(69000)
                        .close(70500)
                        .volume(1000000L)
                        .build()
        );
        when(stockService.getPeriodStockInfo(ticker, period)).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/{ticker}/period", ticker)
                        .param("period", period))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].date").value("20241114"));

        verify(stockService, times(1)).getPeriodStockInfo(ticker, period);
    }

    @Test
    void 기간별_주식_정보_범위_조회_API_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "day";
        String startDate = "20241101";
        String endDate = "20241114";
        List<CandleResponse> responses = Arrays.asList(
                CandleResponse.builder()
                        .date("20241114")
                        .open(70000)
                        .high(71000)
                        .low(69000)
                        .close(70500)
                        .volume(1000000L)
                        .build()
        );
        when(stockService.getPeriodStockInfoByRange(ticker, period, startDate, endDate))
                .thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/{ticker}/period/range", ticker)
                        .param("period", period)
                        .param("startDate", startDate)
                        .param("endDate", endDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(stockService, times(1))
                .getPeriodStockInfoByRange(ticker, period, startDate, endDate);
    }

    @Test
    void 전체_주식_정보_조회_빈_리스트_테스트() throws Exception {
        // Given
        when(stockService.showAllStock()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/stocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(stockService, times(1)).showAllStock();
    }

    @Test
    void 기간별_주식_정보_조회_빈_리스트_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "day";
        when(stockService.getPeriodStockInfo(ticker, period)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/{ticker}/period", ticker)
                        .param("period", period))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(stockService, times(1)).getPeriodStockInfo(ticker, period);
    }

    @Test
    void 기간별_주식_정보_범위_조회_빈_리스트_테스트() throws Exception {
        // Given
        String ticker = "005930";
        String period = "day";
        String startDate = "20241101";
        String endDate = "20241114";
        when(stockService.getPeriodStockInfoByRange(ticker, period, startDate, endDate))
                .thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/stocks/{ticker}/period/range", ticker)
                        .param("period", period)
                        .param("startDate", startDate)
                        .param("endDate", endDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(stockService, times(1))
                .getPeriodStockInfoByRange(ticker, period, startDate, endDate);
    }
}

