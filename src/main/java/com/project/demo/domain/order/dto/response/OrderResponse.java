package com.project.demo.domain.order.dto.response;

import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class OrderResponse {

    private Long orderId;
    private Long userId;
    private Long stockId;
    private String stockName;
    private int price;
    private int quantity;
    private long totalPrice;
    private OrderType orderType;
    private boolean isReserved; // 예약 주문 여부
    private boolean isExecuted; // 체결 완료 여부
    private String message;     // 사용자 알림 메시지
    private LocalDateTime createdAt;

    public static OrderResponse of(Order order) {
        return of(order, null);
    }

    public static OrderResponse of(Order order, String message) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStock().getId(),
                order.getStock().getName(),
                order.getPrice(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getType(),
                order.isReserved(),
                order.isExecuted(),
                message,
                order.getCreatedAt());
    }

}
