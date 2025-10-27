package com.project.demo.common.oauth2;

import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.oauth2.dto.CustomOAuth2User;
import com.project.demo.domain.user.entity.RefreshToken;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.repository.RefreshTokenRepository;
import com.project.demo.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${FRONTEND_URL}")
    private String frontEnd;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getEmail();
        String name = oAuth2User.getName();
        
        log.info("OAuth 사용자 정보 - Email: {}, Name: '{}' (길이: {})", email, name, name != null ? name.length() : 0);

        // 사용자 찾기 또는 자동 회원가입
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            log.info("새로운 사용자 자동 회원가입: {}", email);
            User newUser = User.builder()
                    .email(email)
                    .name(oAuth2User.getName())
                    .userRole(com.project.demo.domain.user.enums.UserRole.ROLE_USER)
                    .balance(10000000)
                    .isDeleted(false)
                    .build();
            return userRepository.save(newUser);
        });

        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getEmail(), user.getUserRole(), user.getName());
        String refreshToken = jwtUtil.createRefreshToken(user.getId());

        // Refresh Token 저장 또는 업데이트
        refreshTokenRepository.findById(user.getId())
                .ifPresentOrElse(
                        rt -> rt.updateValue(refreshToken), // 이미 존재하면 업데이트
                        () -> refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken)) // 없으면 새로 저장
                );

        // 리프레시 토큰을 쿠키에 저장 (로컬 개발: false, 프로덕션: true)
        jwtUtil.addRefreshTokenToCookie(refreshToken, response, false, null);

        // 프론트엔드로 리다이렉트 (토큰을 URL 파라미터로 전달)
        String redirectUrl = frontEnd + "?token=" + accessToken;
        log.info("리다이렉트 URL: {}", redirectUrl);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);

    }
}
