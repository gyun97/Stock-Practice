package com.project.demo.domain.transaction.entity;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.transaction.TransactionType;
import com.project.demo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType type;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int quantity;

    private int totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public Transaction(Long id, TransactionType type, int price, int quantity, Stock stock, User user) {
        this.id = id;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.stock = stock;
        this.user = user;
    }

    // 거래 총 금액 계산
    public void calculateTotalAmount(int price, int quantity) {
        this.totalAmount = price * quantity;
    }

}
