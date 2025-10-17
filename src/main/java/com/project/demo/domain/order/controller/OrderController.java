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
     * 주식 즉시 매수
     *
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

    /**
     * 주식 즉시 매도
     *
     * @param authUser
     * @param ticker
     * @param quantity
     * @return 종목 이름, 수량 포함한 주식 매도 성공 알림 문구
     */
    @PostMapping("/selling/{ticker}")
    public ResponseEntity<ApiResponse<String>> sellingStock(@AuthenticationPrincipal AuthUser authUser, @PathVariable String ticker, @RequestParam int quantity) {
        String response = orderService.sellingStock(authUser.getUserId(), ticker, quantity);
        return ResponseEntity.ok(ApiResponse.createdSuccess(response));
    }

    /**
     * 주식 예약 매수(목표가 이하로 주식 가격 하락하면 자동 구매)
     * @param authUser
     * @param ticker
     * @param quantity
     * @param targetPrice
     * @return
     */
    @PostMapping("/reserve-buying/{ticker}")
    public ResponseEntity<ApiResponse<String>> reserveBuyingStock(@AuthenticationPrincipal AuthUser authUser, @PathVariable String ticker, @RequestParam int quantity, @RequestParam int targetPrice) {
        String response = orderService.reserveBuy(authUser.getUserId(), ticker, quantity, targetPrice);
        return ResponseEntity.ok(ApiResponse.createdSuccess(response));
    }

    /**
     * 주식 예약 매도(목표가 이상으로 주식 가격 상승하면 자동 판매)
     * @param authUser
     * @param ticker
     * @param quantity
     * @param targetPrice
     * @return
     */
    @PostMapping("/reserve-selling/{ticker}")
    public ResponseEntity<ApiResponse<String>> reserveSellingStock(@AuthenticationPrincipal AuthUser authUser, @PathVariable String ticker, @RequestParam int quantity, @RequestParam int targetPrice) {
        String response = orderService.reserveSell(authUser.getUserId(), ticker, quantity, targetPrice);
        return ResponseEntity.ok(ApiResponse.createdSuccess(response));
    }

}
