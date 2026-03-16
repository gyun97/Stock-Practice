package com.project.demo.domain.user.repository;

import com.project.demo.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    // 토큰 값으로 세션 조회 (Access Token 갱신 시 사용)
    Optional<RefreshToken> findByValue(String value);

    // 유저의 특정 토큰 세션 삭제 (단일 기기 로그아웃)
    void deleteByUserIdAndValue(Long userId, String value);

    // 유저의 모든 세션 삭제 (회원탈퇴, 전체 로그아웃)
    void deleteAllByUserId(Long userId);

    // 유저의 모든 세션 조회 (테스트 및 관리 용도)
    java.util.List<com.project.demo.domain.user.entity.RefreshToken> findAllByUserId(Long userId);
}
