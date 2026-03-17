package com.project.demo.domain.user.repository;

import com.project.demo.domain.user.entity.RefreshToken;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.List;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    // 토큰 값으로 세션 조회 (Access Token 갱신 시 사용)
    Optional<RefreshToken> findByValue(String value);

    // 유저의 모든 세션 조회 (테스트 및 관리 용도)
    List<RefreshToken> findAllByUserId(Long userId);
}
