package com.project.demo.common.jwt;

import com.project.demo.domain.user.entity.AuthUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthUser authUser;

    public JwtAuthenticationToken(AuthUser authUser) {
        super(authUser.getAuthorities()); // AbstractAuthenticationToken의 생성자에 권한 정보 전달. 이 사용자가 가진 ROLE(USER/ADMIN 등)을 SecurityContext에 함께 저장
        this.authUser = authUser;
        setAuthenticated(true); // 이 토큰은 이미 검증된 상태임을 명시
    }

    // 비밀번호 등 자격 증명 정보를 반환하는 부분이지만 JWT 인증은 이미 검증이 끝났기 때문에 비밀번호가 필요 없음
    @Override
    public Object getCredentials() {
        return null;
    }

    // 인증된 사용자 주체(Principal)를 반환. @AuthenticationPrincipal 로 접근되는 값
    @Override
    public Object getPrincipal() {
        return authUser;
    }
}