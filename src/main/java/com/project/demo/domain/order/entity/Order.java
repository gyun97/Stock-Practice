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
    @Column(name = "execution_type", nullable = false)
    private OrderType type;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int quantity;

    private int totalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    private Order order;

    @Builder
    public Order(Long id, OrderType type, int price, int quantity, int totalAmount, Stock stock, User user, Order order) {
        this.id = id;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.stock = stock;
        this.user = user;
        this.order = order;
    }

    /*
    거래 총 금액 계산
     */
    public void calculateTotalAmount(int price, int quantity) {
        this.totalAmount = price * quantity;
    }













}
