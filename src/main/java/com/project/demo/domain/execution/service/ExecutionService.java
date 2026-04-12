package com.project.demo.domain.execution.service;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;

public interface ExecutionService {
    // 즉시 주문용 (부모 트랜잭션 참여)
    void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    // 예약 주문용 (독립 트랜잭션 보장)
    void executeReservedBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    void executeReservedSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    /**
     * 특정 종목의 예약 주문 체결 (이벤트 기반)
     *
     * @param ticker       종목 코드
     * @param currentPrice 현재가
     */
    void executeReservedOrdersForTicker(String ticker, int currentPrice);
}
