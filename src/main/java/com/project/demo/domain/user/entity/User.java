package com.project.demo.domain.user.entity;

import com.project.demo.common.entity.TimeStamped;
import com.project.demo.domain.transaction.entity.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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
    private double balance;

    @OneToMany(mappedBy = "user")
    private List<Transaction> transactions;

    @Builder
    public User(Long id, String password, String nickname, double balance) {
        this.id = id;
        this.password = password;
        this.nickname = nickname;
        this.balance = balance;
    }


}
