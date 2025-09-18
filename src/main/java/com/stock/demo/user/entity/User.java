package com.stock.demo.user.entity;

import com.stock.demo.common.entity.TimeStamped;
import com.stock.demo.domain.portfolio.entity.Portfolio;
import com.stock.demo.domain.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column(nullable = false)
    private Long totalAsset;

    @Column(nullable = false)
    private Long availableAsset;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Portfolio portfolio;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Transaction transaction;

    @Builder
    public User(Long id, String password, String nickname, Long totalAsset, Long availableAsset) {
        this.id = id;
        this.password = password;
        this.nickname = nickname;
        this.totalAsset = totalAsset;
        this.availableAsset = availableAsset;
    }


}
