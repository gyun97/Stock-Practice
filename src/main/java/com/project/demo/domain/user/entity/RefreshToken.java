package com.project.demo.domain.user.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RedisHash(value = "refreshToken", timeToLive = 1209600) // 14 days = 2 weeks TTL
public class RefreshToken {

    @Id
    private String id; // 세션 UUID (기기별 고유 식별자)

    @Indexed
    private Long userId; // 유저 PK

    @Indexed
    private String value; // 토큰 값

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
