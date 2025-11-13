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
    private int avgPrice; // 평균 구매 단가

    @Column(nullable = false)
    private long purchaseAmount; // 매입단가

    @Column(nullable = false)
    private int totalQuantity; // 해당 종목 총 보유 수량

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String stockName;

    @Column(nullable = false)
    private String ticker;

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
    public UserStock(Long id, String ticker,int avgPrice, int totalQuantity, User user, Stock stock, Portfolio portfolio, String userName, String stockName) {
        this.id = id;
        this.ticker = ticker;
        this.avgPrice = avgPrice;
        this.totalQuantity = totalQuantity;
        this.user = user;
        this.stock = stock;
        this.portfolio = portfolio;
        this.userName = userName;
        this.stockName = stockName;
    }

//    public double getAvgPrice() {
//        return totalQuantity == 0 ? 0 : (double) purchaseAmount / totalQuantity;
//    }

    /*
    매수 시 총 구매액 증가
     */
    public void increasePurchaseAmount(long amount) {
        this.purchaseAmount += amount;
    }

    /*
    매도 시 총 구매액 감소
     */
    public void decreasePurchaseAmount(int sellQuantity) {
        this.purchaseAmount -= (long) sellQuantity * this.avgPrice;
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
