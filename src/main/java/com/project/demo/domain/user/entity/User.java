package com.project.demo.domain.user.entity;

import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.execution.entity.Execution;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private double balance; // 잔액

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false; // 탈퇴 여부

    private LocalDateTime withdrawalAt; // 탈퇴일

    @Enumerated(EnumType.STRING)
    private UserRole userRole; // 운영자/일반 유저

    @Column(length = 100, unique = true, nullable = false)
    private String email;

    @OneToMany(mappedBy = "user")
    private List<Order> orders;

    @Builder
    public User(Long id, String password, String name, double balance, UserRole userRole, String email, boolean isDeleted) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.balance = balance;
        this.userRole = userRole;
        this.email = email;
        this.isDeleted = isDeleted;
    }

    /*
    유저 회원가입시 새 유저 객체 생성
     */
    @Builder
    public static User createNewUser(String email, String name, String encodedPassword, UserRole role) {
        return User.builder()
                .email(email)
                .name(name)
                .password(encodedPassword)
                .userRole(role)
                .balance(10000000)
                .isDeleted(false)
                .build();
    }

    /*
    탈퇴 회원 재가입
     */
    public void reactivate(String newPassword, String newName, UserRole newRole) {
        this.password = newPassword;
        this.name = newName;
        this.userRole = newRole;
        this.isDeleted = false;
        this.withdrawalAt = null;
        this.balance = 10000000;
    }

    /*
    유저 개인 정보 수정
     */
    public void updateUserInfo(UpdateUserInfoRequest request) {
        if (request.getNewEmail() != null) this.email = request.getNewEmail();
        if (request.getNewName() != null) this.name = request.getNewName();
    }

    /*
    회원 탈퇴 상태로 전환(소프트 딜리트)
     */
    public void updateIsDeleted() {
        this.isDeleted = true;
    }

    /*
    비밀번호 변경
     */
    public void changePassword(String newPassword) {
        this.password = newPassword;
    }
}

