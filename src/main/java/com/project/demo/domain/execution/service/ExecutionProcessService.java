package com.project.demo.domain.execution.service;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;

public interface ExecutionProcessService {
    // 즉시 주문용 (부모 트랜잭션 참여)
    void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice);

    // 예약 주문 체결용 위임 인터페이스는 제거됨 (executeReservedOrder 로 통합)
    // 예약 주문용 개별 실행 위임 (단건 조회 및 트랜잭션 분리)
    void executeReservedOrder(Long orderId, int currentPrice);
}
