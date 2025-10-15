package com.project.demo.domain.userstock.entity;

import com.project.demo.common.util.TimeStamped;
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

    private long totalAsset;

    private double avgReturnRate; // 평균 수익률

    private int totalQuantity; // 주식 총 보유 수량

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public UserStock(Long id, long totalAsset, double avgReturnRate, int totalQuantity, User user) {
        this.id = id;
        this.totalAsset = totalAsset;
        this.avgReturnRate = avgReturnRate;
        this.totalQuantity = totalQuantity;
        this.user = user;
    }
}
