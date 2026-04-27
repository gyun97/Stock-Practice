package com.project.demo.domain.order.controller;

import com.project.demo.common.jwt.JwtAuthenticationToken;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrderController 단위 테스트
 */
@WebMvcTest(OrderController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false) // Spring Security 필터를 비활성화
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

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
    void 주식_즉시_매수_API_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 10;
        String responseMessage = "삼성전자 10주 매수 완료";
        when(orderService.buyingStock(anyLong(), eq(ticker), eq(quantity))).thenReturn(responseMessage);

        // When & Then
        mockMvc.perform(post("/api/v1/orders/buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(responseMessage));

        verify(orderService, times(1)).buyingStock(anyLong(), eq(ticker), eq(quantity));
    }

    @Test
    void 주식_즉시_매도_API_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 5;
        String responseMessage = "삼성전자 5주 매도 완료";
        when(orderService.sellingStock(anyLong(), eq(ticker), eq(quantity))).thenReturn(responseMessage);

        // When & Then
        mockMvc.perform(post("/api/v1/orders/selling/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(responseMessage));

        verify(orderService, times(1)).sellingStock(anyLong(), eq(ticker), eq(quantity));
    }

    @Test
    void 주식_예약_매수_API_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 10;
        int targetPrice = 70000;
        String responseMessage = "삼성전자 예약 매수 주문이 등록되었습니다. (목표가: 70000원)";
        when(orderService.reserveBuy(anyLong(), eq(ticker), eq(quantity), eq(targetPrice)))
                .thenReturn(responseMessage);

        // When & Then
        mockMvc.perform(post("/api/v1/orders/reserve-buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity))
                        .param("targetPrice", String.valueOf(targetPrice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(responseMessage));

        verify(orderService, times(1)).reserveBuy(anyLong(), eq(ticker), eq(quantity), eq(targetPrice));
    }

    @Test
    void 주식_예약_매도_API_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 5;
        int targetPrice = 75000;
        String responseMessage = "삼성전자 예약 매도 주문이 등록되었습니다. (목표가: 75000원)";
        when(orderService.reserveSell(anyLong(), eq(ticker), eq(quantity), eq(targetPrice)))
                .thenReturn(responseMessage);

        // When & Then
        mockMvc.perform(post("/api/v1/orders/reserve-selling/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity))
                        .param("targetPrice", String.valueOf(targetPrice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(responseMessage));

        verify(orderService, times(1)).reserveSell(anyLong(), eq(ticker), eq(quantity), eq(targetPrice));
    }

    @Test
    void 예약_주문_취소_API_테스트() throws Exception {
        // Given
        Long orderId = 1L;
        OrderResponse response = new OrderResponse(
                orderId,
                1L,
                1L,
                "삼성전자",
                70000,
                10,
                700000,
                OrderType.BUY,
                true,
                false,
                LocalDateTime.now()
        );
        when(orderService.cancelReservation(orderId)).thenReturn(response);

        // When & Then
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.stockName").value("삼성전자"));

        verify(orderService, times(1)).cancelReservation(orderId);
    }

    @Test
    void 내_주문_내역_전체_조회_API_테스트() throws Exception {
        // Given
        List<OrderResponse> responses = Arrays.asList(
                new OrderResponse(1L, 1L, 1L, "삼성전자", 70000, 10, 700000, OrderType.BUY, false, true, LocalDateTime.now()),
                new OrderResponse(2L, 1L, 1L, "삼성전자", 75000, 5, 375000, OrderType.SELL, false, true, LocalDateTime.now())
        );
        when(orderService.getMyAllOrders(anyLong())).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].orderId").value(1L))
                .andExpect(jsonPath("$.data[0].orderType").value("BUY"))
                .andExpect(jsonPath("$.data[1].orderId").value(2L))
                .andExpect(jsonPath("$.data[1].orderType").value("SELL"));

        verify(orderService, times(1)).getMyAllOrders(anyLong());
    }

    @Test
    void 내_일반_주문_내역_조회_API_테스트() throws Exception {
        // Given
        List<OrderResponse> responses = Arrays.asList(
                new OrderResponse(1L, 1L, 1L, "삼성전자", 70000, 10, 700000, OrderType.BUY, false, true, LocalDateTime.now())
        );
        when(orderService.getNormalOrders(anyLong())).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/orders/normal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(orderService, times(1)).getNormalOrders(anyLong());
    }

    @Test
    void 내_예약_주문_내역_조회_API_테스트() throws Exception {
        // Given
        List<OrderResponse> responses = Arrays.asList(
                new OrderResponse(1L, 1L, 1L, "삼성전자", 70000, 10, 700000, OrderType.BUY, true, false, LocalDateTime.now())
        );
        when(orderService.getReservationOrders(anyLong())).thenReturn(responses);

        // When & Then
        mockMvc.perform(get("/api/v1/orders/reservation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(orderService, times(1)).getReservationOrders(anyLong());
    }

    @Test
    void 예약_주문_취소_주문_없음_예외_테스트() throws Exception {
        // Given
        Long orderId = 999L;
        when(orderService.cancelReservation(orderId))
                .thenThrow(new com.project.demo.common.exception.order.NotFoundOrderException());

        // When & Then
        mockMvc.perform(delete("/api/v1/orders/{orderId}", orderId))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).cancelReservation(orderId);
    }

    @Test
    void 주식_즉시_매수_잔액_부족_예외_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 1000;
        when(orderService.buyingStock(anyLong(), eq(ticker), eq(quantity)))
                .thenThrow(new com.project.demo.common.exception.order.NotEnoughMoneyException());

        // When & Then
        mockMvc.perform(post("/api/v1/orders/buying/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).buyingStock(anyLong(), eq(ticker), eq(quantity));
    }

    @Test
    void 주식_즉시_매도_보유_주식_부족_예외_테스트() throws Exception {
        // Given
        String ticker = "005930";
        int quantity = 1000;
        when(orderService.sellingStock(anyLong(), eq(ticker), eq(quantity)))
                .thenThrow(new com.project.demo.common.exception.order.NotEnoughStockException());

        // When & Then
        mockMvc.perform(post("/api/v1/orders/selling/{ticker}", ticker)
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().isBadRequest());

        verify(orderService, times(1)).sellingStock(anyLong(), eq(ticker), eq(quantity));
    }
}

