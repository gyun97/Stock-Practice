package com.project.demo.common.auth.service;

import com.project.demo.common.auth.entity.RefreshToken;
import com.project.demo.common.auth.repository.RefreshTokenRepository;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.user.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/*
Refresh Token을 통한 Access Token 재발급 서비스
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    /*
    Refresh Token을 이용한 Aceess Token 재발급 메서드
    */
    public String refreshAccessToken(String refreshToken) {

        // 1.  Refresh Token 유효성 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new IllegalStateException("유효하지 않은 Refresh Token입니다.");
        }

        // 2. 해당 Refresh Token이 DB에 실제 존재하는지 확인
        Long userId = Long.parseLong(jwtUtil.extractClaims(refreshToken).getSubject());
        if (!isValid(userId, refreshToken)) {
            throw new NoSuchElementException("DB에 해당 사용자의 유효한 Refresh Token이 존재하지 않습니다.");
        }

        // 3. 새 Access Token 발급
        String email = jwtUtil.extractClaims(refreshToken).get("email", String.class);
        String role = jwtUtil.extractClaims(refreshToken).get("userRole", String.class);
        String name = jwtUtil.extractClaims(refreshToken).get("name", String.class);

        return jwtUtil.createAccessToken(userId, email, UserRole.of(role), name);
    }

    /*
    DB에 있는 토큰과의 비교 검증
     */
    public boolean isValid(Long userId, String refreshToken) {
        RefreshToken existingToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("DB에 해당 사용자의 유효한 Refresh Token이 존재하지 않습니다."));

        return refreshToken.equals(existingToken.getValue());
    }
}
