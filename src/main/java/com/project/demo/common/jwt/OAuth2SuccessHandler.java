package com.project.demo.common.jwt;

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
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
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

        DefaultOAuth2User oAuth2User = (DefaultOAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String name = (String) oAuth2User.getAttributes().get("name");

        User user = userRepository.findByEmail(email).orElseThrow();

        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getEmail(), user.getUserRole(), user.getName());
        String refreshToken = jwtUtil.createRefreshToken(user.getId());

        // 토큰 발급
        jwtUtil.addAccessTokenToHeader(accessToken, response);
        jwtUtil.addRefreshTokenToCookie(refreshToken, response, true, null);

        log.info("Access Token: {}", accessToken);
        log.info("Refresh Token: {}", refreshToken);

        refreshTokenRepository.save(RefreshToken.builder() //새 Refresh Token 저장
                .key(user.getId())
                .value(refreshToken)
                .build());

        // 토큰 반환
        jwtUtil.addAccessTokenToHeader(accessToken, response);
        jwtUtil.addRefreshTokenToCookie(refreshToken, response, true, null);

        getRedirectStrategy().sendRedirect(request, response, "https://localhost:5173");
    }
}
