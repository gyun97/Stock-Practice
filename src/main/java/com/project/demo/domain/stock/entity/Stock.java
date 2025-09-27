package com.project.demo.domain.stock.entity;

import com.project.demo.common.time.TimeStamped;
import com.project.demo.domain.stock.enums.Market;
import com.project.demo.domain.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "stocks")
public class Stock extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long id;

    // 주식 식별 코드
    @Column(nullable = false)
    private String symbol;

    @Column(name = "stock_name", nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Market market;

    @Column(nullable = false)
    private int price;


    @OneToMany(mappedBy = "stock")
    private List<Transaction> transactions;

    @Builder
    public Stock(Long id, String symbol, String name, Market market, int price) {
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.market = market;
        this.price = price;
    }


}
