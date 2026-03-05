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
import java.util.UUID;

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

        @Value("${cookie.secure:false}")
        private boolean cookieSecure;

        @Value("${cookie.domain:null}")
        private String cookieDomain;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) throws IOException {

                CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
                String email = oAuth2User.getEmail();
                String name = oAuth2User.getName();

                log.info("OAuth 사용자 정보 - Email: {}, Name: '{}' (길이: {})", email, name,
                                name != null ? name.length() : 0);

                // CustomOAuth2UserService에서 이미 DB 저장/연결이 완료되었으므로 ID로 조회만 함
                User user = userRepository.findById(oAuth2User.getId())
                                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + email));

                String accessToken = jwtUtil.createAccessToken(user.getId(), user.getEmail(), user.getUserRole(),
                                user.getName());
                String refreshToken = jwtUtil.createRefreshToken(user.getId());

                // Refresh Token 기기별 세션 저장 (멀티 기기 지원: UUID로 각 기기를 구분)
                refreshTokenRepository.save(RefreshToken.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(user.getId())
                                .value(refreshToken)
                                .build());

                // 리프레시 토큰을 쿠키에 저장
                jwtUtil.addRefreshTokenToCookie(refreshToken, response, cookieSecure, cookieDomain);

                // 프론트엔드로 리다이렉트 (토큰을 URL 파라미터로 전달)
                String redirectUrl = frontEnd + "?token=" + accessToken;
                log.info("리다이렉트 URL: {}", redirectUrl);
                getRedirectStrategy().sendRedirect(request, response, redirectUrl);

        }
}
