package com.project.demo.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id; // 세션 UUID (기기별 고유 식별자)

    @Column(name = "user_id", nullable = false)
    private Long userId; // 유저 PK

    @Column(name = "rt_value", nullable = false, length = 500)
    private String value; // 토큰 값

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public RefreshToken(String id, Long userId, String value, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.value = value;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public RefreshToken updateValue(String token) {
        this.value = token;
        return this;
    }
}
