package com.project.demo.common.auth.controller;

import com.project.demo.common.auth.service.RefreshTokenService;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.user.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    /*
        JWT 발급 테스트 메서드
     */
//    @PostMapping("/issue")
//    public String issueToken() {
//        String accessToken = jwtUtil.createAccessToken(1L, "fdasf", UserRole.ROLE_USER, "123");
//        String refreshToken = jwtUtil.createRefreshToken(1L);
//
//        return accessToken + "\n" + refreshToken;
//    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refreshAccessToken() {
        String refreshToken = jwtUtil.getRefreshTokenFromCookie(request); // 쿠키에서 Refresh Token 꺼내기
        String newAccessToken = refreshTokenService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.createSuccess(newAccessToken));
    }
}