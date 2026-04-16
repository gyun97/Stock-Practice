package com.project.demo.domain.execution.service;

import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;

public interface ExecutionProcessService {
    // 즉시 주문용 (부모 트랜잭션 참여)
    void processBuy(Order order, int price, long totalPrice);

    void processSell(Order order, int price, long totalPrice);

    // 예약 주문용 개별 실행 위임 (단건 조회 및 트랜잭션 분리)
    Order executeReservedOrder(Long orderId, int currentPrice);
}
