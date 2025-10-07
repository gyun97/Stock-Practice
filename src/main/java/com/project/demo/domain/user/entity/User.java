package com.project.demo.domain.user.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.transaction.entity.Transaction;
import com.project.demo.domain.user.enums.UserRole;
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
    private String name;

    @Column(nullable = false)
    private double balance;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Enumerated(EnumType.STRING)
    private UserRole userRole; // 운영자/일반 유저

    @Column(length = 100, unique = true, nullable = false)
    private String email;

    private long refreshToken;

    @OneToMany(mappedBy = "user")
    private List<Transaction> transactions;

    @Builder
    public User(Long id, String password, String name, double balance, UserRole userRole, String email) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.balance = balance;
        this.userRole = userRole;
        this.email = email;
    }


}
