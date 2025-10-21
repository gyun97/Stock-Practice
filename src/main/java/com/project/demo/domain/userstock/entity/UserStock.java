package com.project.demo.domain.userstock.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_stocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStock extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_stock_id")
    private Long id;

    @Column(nullable = false)
    private long totalAsset; // 총 자산

    @Column(nullable = false)
    private double avgReturnRate; // 평균 수익률

    @Column(nullable = false)
    private int avgPrice; // 평균 구매 단가

    @Column(nullable = false)
    private int totalQuantity; // 해당 종목 총 보유 수량

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String stockName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_id", nullable = false)
    private Portfolio portfolio;


    @Builder
    public UserStock(Long id, long totalAsset, double avgReturnRate, int avgPrice, int totalQuantity, User user, Stock stock, Portfolio portfolio, String usrName, String stockName) {
        this.id = id;
        this.totalAsset = totalAsset;
        this.avgReturnRate = avgReturnRate;
        this.avgPrice = avgPrice;
        this.totalQuantity = totalQuantity;
        this.user = user;
        this.stock = stock;
        this.portfolio = portfolio;
        this.userName = usrName;
        this.stockName = stockName;
    }

    /*
    해당 보유 종목 평균단가 갱신
     */
    public void updateAveragePrice(int avgPrice) {
        this.avgPrice = avgPrice;
    }

    /*
    해당 보유 종목 보유수량 갱신
     */
    public void updateQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

}
