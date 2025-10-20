package com.project.demo.domain.order.dto.response;

import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class OrderResponse {

    private Long orderId;
    private Long userId;
    private Long stockId;
    private int price;
    private int quantity;
    private int totalPrice;
    private OrderType orderType;
    private boolean isReserved;  // 예약 주문 여부
    private boolean isExecuted;  // 체결 완료 여부

    public static OrderResponse of(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getStock().getId(),
                order.getPrice(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getType(),
                order.isReserved(),
                order.isExecuted()
        );
    }


}
