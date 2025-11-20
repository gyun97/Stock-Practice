package com.project.demo.domain.userstock.controller;

import com.project.demo.common.jwt.JwtAuthenticationToken;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.userstock.dto.response.UserStockResponse;
import com.project.demo.domain.userstock.service.UserStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserStockController 단위 테스트
 */
@WebMvcTest(UserStockController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class UserStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserStockService userStockService;

    @MockitoBean
    private JwtUtil jwtUtil;

    private AuthUser authUser;

    @BeforeEach
    void setUp() {
        authUser = new AuthUser(1L, "test@example.com", UserRole.ROLE_USER, "테스트 사용자");
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(authUser);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 내_보유_주식_조회_API_테스트() throws Exception {
        // Given
        List<UserStockResponse> responses = Arrays.asList(
                UserStockResponse.of("005930", "삼성전자", 10, 70000, 75000),
                UserStockResponse.of("000660", "SK하이닉스", 5, 150000, 155000)
        );
        when(userStockService.getUserStocksByUserId(anyLong())).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/userstocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ticker").value("005930"))
                .andExpect(jsonPath("$.data[0].companyName").value("삼성전자"))
                .andExpect(jsonPath("$.data[0].totalQuantity").value(10))
                .andExpect(jsonPath("$.data[0].avgPrice").value(70000))
                .andExpect(jsonPath("$.data[0].currentPrice").value(75000))
                .andExpect(jsonPath("$.data[1].ticker").value("000660"))
                .andExpect(jsonPath("$.data[1].companyName").value("SK하이닉스"));

        verify(userStockService, times(1)).getUserStocksByUserId(anyLong());
    }

    @Test
    void 보유_주식_없을_때_API_테스트() throws Exception {
        // Given
        when(userStockService.getUserStocksByUserId(anyLong())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/userstocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(userStockService, times(1)).getUserStocksByUserId(anyLong());
    }
}

