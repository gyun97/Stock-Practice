package com.stock.demo.domain.stock.entity;

import com.stock.demo.common.entity.TimeStamped;
import com.stock.demo.domain.stock.enums.Market;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private Integer price;

    @Builder
    public Stock(Long id, String symbol, String name, Market market, Integer price) {
        this.id = id;
        this.symbol = symbol;
        this.name = name;
        this.market = market;
        this.price = price;
    }


}
