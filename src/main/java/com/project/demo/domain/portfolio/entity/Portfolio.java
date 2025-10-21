package com.project.demo.domain.portfolio.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.userstock.entity.UserStock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends TimeStamped {

    private static final int PRINCIPAL = 10000000; // 초기 원금

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "port_id")
    private Long id;

    @Column(nullable = false)
    private long balance; // 가용 자산(잔액)

    @Column(nullable = false)
    private long totalAsset; // 총 자산(가용 금액(balance) + 보유 주식 총액(stockAsset))

    @Column(nullable = false)
    private int totalQuantity; // 보유 주식 수량

    @Column(nullable = false)
    private double returnRate;

    @Column(nullable = false)
    private int holdCount; // 보유 종목 수

    @Column(nullable = false)
    private int stockAsset; // 보유 주식 총액

    // 단방향
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserStock> userStocks;


    @Builder
    public Portfolio(Long id, long balance, long totalAsset, int totalQuantity, int stockAsset, double avgReturnRate, int holdCount, User user) {
        this.id = id;
        this.balance = balance;
        this.totalAsset = totalAsset;
        this.totalQuantity = totalQuantity;
        this.stockAsset = stockAsset;
        this.returnRate = avgReturnRate;
        this.holdCount = holdCount;
        this.user = user;
    }


// -----------------------------매수 ----------------------------

    public void increaseStockAsset(int amount) {
        this.stockAsset += amount;
    }

    public void decreaseBalance(int amount) {
        this.balance -= amount;
    }

    public void increaseTotalQuantity(int quantity) {
        this.totalQuantity += quantity;
    }

    public void updateHoldCount() {
        this.holdCount = this.userStocks.size();
    }

    // -----------------------------매도 ----------------------------
    /*
    주식 매도로 인한 주식 자산 감소 반영
     */
    public void decreaseStockAsset(int amount) {
        this.stockAsset = Math.max(0, this.stockAsset - amount);
    }

    /*
    주식 매도로 인한 보유 현금 증가 반영
     */
    public void increaseBalance(int amount) {
        this.balance += amount;
    }

    /*
    주식 매도로 인한 주식 수량 감소 반영
     */
    public void decreaseTotalQuantity(int quantity) {
        this.totalQuantity = Math.max(0, this.totalQuantity - quantity);
    }


    /*
    총 자산 변화 반영
     */
    public void recalculateTotalAsset() {
        this.totalAsset = this.balance + this.stockAsset;
    }

    /*
    수익률 변화 반영
     */
    public void updateReturnRate() {
        this.returnRate = ((double) (this.totalAsset - PRINCIPAL) / PRINCIPAL) * 100;
    }
}
