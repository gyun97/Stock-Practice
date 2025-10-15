package com.project.demo.domain.order.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.order.service.OrderService;
import com.project.demo.domain.user.entity.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1/orders")
@RestController
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    /**
     * 주식 매수
     * @param authUser
     * @param ticker
     * @param quantity
     * @return 종목 이름, 수량 포함한 주식 매수 성공 알림 문구
     */
    @PostMapping("/buying/{ticker}")
    public ResponseEntity<ApiResponse<String>> buyingStock(@AuthenticationPrincipal AuthUser authUser, @PathVariable String ticker, @RequestParam int quantity) {
        String response = orderService.buyingStock(authUser.getUserId(), ticker, quantity);
        return ResponseEntity.ok(ApiResponse.createdSuccess(response));
    }

    @PostMapping("/selling/{ticker}")
    public ResponseEntity<ApiResponse<String>> sellingStock(@AuthenticationPrincipal AuthUser authUser, @PathVariable String ticker, @RequestParam int quantity) {
        String response = orderService.sellingStock(authUser.getUserId(), ticker, quantity);
        return ResponseEntity.ok(ApiResponse.createdSuccess(response));
    }






}
