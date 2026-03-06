package com.project.demo.domain.order.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType type; // BUY or SELL

    @Column(nullable = false)
    private int price; // 주문가(예약가 포함)

    @Column(nullable = false)
    private int quantity;

    private long totalPrice;

    private boolean isReserved; // 예약 주문 여부

    private boolean isExecuted; // 체결 완료 여부

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public Order(Long id, OrderType type, int price, int quantity, long totalPrice, Stock stock, User user,
            boolean isReserved, boolean isExecuted) {
        this.id = id;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.stock = stock;
        this.user = user;
        this.isReserved = isReserved;
        this.isExecuted = isExecuted;
    }

    /*
     * 거래 총 금액 계산
     */
    public void calculateTotalAmount(int price, int quantity) {
        this.totalPrice = (long) price * quantity;
    }

    /*
     * 주문 체결 상태 업데이트
     */
    public void markExecuted() {
        this.isExecuted = true;
    }
}
