package com.project.demo.domain.execution.service;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import io.micrometer.observation.annotation.Observed;

public interface ExecutionService {
    // 즉시 주문용 (부모 트랜잭션 참여)
    void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    // 예약 주문 체결용 위임 인터페이스는 제거됨 (executeReservedOrder 로 통합)

    /**
     * 특정 종목의 예약 주문 체결 (이벤트 기반)
     *
     * @param ticker       종목 코드
     * @param currentPrice 현재가
     */
    void executeReservedOrdersForTicker(String ticker, int currentPrice);
}
