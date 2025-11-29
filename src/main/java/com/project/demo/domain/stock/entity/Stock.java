package com.project.demo.domain.stock.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.stock.enums.Market;
import com.project.demo.domain.userstock.entity.UserStock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
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
    private String ticker; // 종목 코드

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Market market;

    private Long volume; // 누적 거래량

    @Column(columnDefinition = "TEXT")
    private String outline; // 기업 개요

    @OneToMany(mappedBy = "stock")
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "stock")
    private List<UserStock> userStocks = new ArrayList<>();


    @Builder
    public Stock(Long id, String ticker, String name, Market market, Long volume) {
        this.id = id;
        this.ticker = ticker;
        this.name = name;
        this.market = market;
        this.volume = volume;
        this.outline = null;
    }

    /**
     * 기업 개요(outline) 설정
     */
    public void setOutline(String outline) {
        this.outline = outline;
    }

}
