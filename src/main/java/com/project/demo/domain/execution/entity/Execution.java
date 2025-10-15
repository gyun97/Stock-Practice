package com.project.demo.domain.execution.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "executions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Execution extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false)
    private OrderType type;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int quantity;

    private int totalPrice;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn
    private Order order;

    @Builder
    public Execution(Long id, OrderType type, int price, int quantity, Stock stock, User user, Order order) {
        this.id = id;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.order = order;
    }


    /*
    거래 총 금액 계산
     */
    public void calculateTotalAmount(int price, int quantity) {
        this.totalAmount = price * quantity;
    }

}
