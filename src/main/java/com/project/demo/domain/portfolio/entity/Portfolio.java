package com.project.demo.domain.portfolio.entity;

import com.project.demo.common.entity.TimeStamped;
import com.project.demo.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Portfolio extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "portfolio_id")
    private Long id;

    @Column(nullable = false)
    private long totalAsset;

    @Column(nullable = false)
    private double avgReturnRate;

    // 단방향
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Builder
    public Portfolio(Long id, long totalAsset, double avgReturnRate, User user) {
        this.id = id;
        this.totalAsset = totalAsset;
        this.avgReturnRate = avgReturnRate;
        this.user = user;
    }
}
