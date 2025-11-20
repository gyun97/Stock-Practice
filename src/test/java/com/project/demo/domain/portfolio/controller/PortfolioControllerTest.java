package com.project.demo.domain.portfolio.controller;

import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.dto.response.RankingResponse;
import com.project.demo.domain.portfolio.service.PortfolioService;
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
 * PortfolioController 단위 테스트
 */
@WebMvcTest(controllers = PortfolioController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false) // Spring Security 필터를 비활성화
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    void 포트폴리오_조회_API_테스트() throws Exception {
        // Given
        Long userId = 1L;
        PortfolioResponse response = new PortfolioResponse(
                10000000L,
                5000000L,
                15000000L,
                3,
                100,
                50.0
        );
        when(portfolioService.getMyPortfolio(userId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/portfolios/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(10000000L))
                .andExpect(jsonPath("$.data.totalAsset").value(15000000L))
                .andExpect(jsonPath("$.data.returnRate").value(50.0));

        verify(portfolioService, times(1)).getMyPortfolio(userId);
    }

    @Test
    void 랭킹_조회_API_테스트() throws Exception {
        // Given
        int limit = 10;
        List<RankingResponse> rankings = Arrays.asList(
                new RankingResponse(1L, "사용자1", 20000000L, 100.0, 1),
                new RankingResponse(2L, "사용자2", 15000000L, 50.0, 2)
        );
        when(portfolioService.getRanking(limit)).thenReturn(rankings);

        // When & Then
        mockMvc.perform(get("/api/v1/portfolios/ranking")
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].totalAsset").value(20000000L));

        verify(portfolioService, times(1)).getRanking(limit);
    }

    @Test
    void 랭킹_조회_기본값_API_테스트() throws Exception {
        // Given
        List<RankingResponse> rankings = Arrays.asList(
                new RankingResponse(1L, "사용자1", 20000000L, 100.0, 1)
        );
        when(portfolioService.getRanking(10)).thenReturn(rankings); // 기본값 10

        // When & Then
        mockMvc.perform(get("/api/v1/portfolios/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(portfolioService, times(1)).getRanking(10);
    }

    @Test
    void 포트폴리오_조회_포트폴리오_없음_예외_테스트() throws Exception {
        // Given
        Long userId = 999L;
        when(portfolioService.getMyPortfolio(userId))
                .thenThrow(new com.project.demo.common.exception.portfolio.NotFoundPortfolioException());

        // When & Then
        mockMvc.perform(get("/api/v1/portfolios/users/{userId}", userId))
                .andExpect(status().isBadRequest());

        verify(portfolioService, times(1)).getMyPortfolio(userId);
    }

    @Test
    void 랭킹_조회_빈_리스트_테스트() throws Exception {
        // Given
        when(portfolioService.getRanking(10)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/portfolios/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(portfolioService, times(1)).getRanking(10);
    }
}

