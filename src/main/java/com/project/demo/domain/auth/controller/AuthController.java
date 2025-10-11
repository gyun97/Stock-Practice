package com.project.demo.domain.auth.controller;

import com.project.demo.domain.auth.service.AuthService;
import com.project.demo.domain.auth.service.AuthServiceImpl;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.SignUpResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final JwtUtil jwtUtil;
    private final AuthServiceImpl refreshTokenService;
    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final AuthService authService;

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

    /**
     * 회원가입 API
     * @param signUpRequest
     * @return [Access Token, Refresh Token]
     */

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest signUpRequest) {
        SignUpResponse response = authService.signUp(signUpRequest);

        jwtUtil.addAccessTokenToHeader(response.getAccessToken(), httpServletResponse); // 응답 헤더에 Access Token 저장
        jwtUtil.addRefreshTokenToCookie(response.getRefreshToken(), httpServletResponse, true, null); // 응답 쿠키에 Refresh Token 저장

        return ResponseEntity.ok(ApiResponse.createdSuccess(response)); // 201 코드
    }

    /**
     * 로그인 API
     * @param loginRequest
     * @return [Access Token, Refresh Token]
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);

        jwtUtil.addAccessTokenToHeader(response.getAccessToken(), httpServletResponse); // 응답 헤더에 Access Token 저장
        jwtUtil.addRefreshTokenToCookie(response.getRefreshToken(), httpServletResponse, true, null); // // 응답 쿠키에 Refresh Token 저장

        return ResponseEntity.ok(ApiResponse.createdSuccess(response)); // 201 코드
    }

    /**
     * Access Token 재발급 API
     * @return [Access Token]
     */
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<String>> refreshAccessToken() {

        String refreshToken = jwtUtil.getRefreshTokenFromCookie(httpServletRequest); // 쿠키에서 Refresh Token 꺼내기
        String newAccessToken = refreshTokenService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.requestSuccess(newAccessToken));
    }
}