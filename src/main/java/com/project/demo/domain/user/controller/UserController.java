package com.project.demo.domain.user.controller;

import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.response.SignUpResponse;
import com.project.demo.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequiredArgsConstructor
@RequestMapping("api/v1/users")
@Slf4j
public class UserController {

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final HttpServletResponse httpServletResponse;


//    @PostMapping("/sign-up")
//    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(@Valid @RequestBody SignUpRequest signUpRequest) {
//        SignUpResponse response = userService.signUp(signUpRequest);
//
//        log.info("Access Token: {}", response.getAccessToken());
//        log.info("Refresh Token: {}", response.getRefreshToken());
//
//        jwtUtil.addAccessTokenToHeader(response.getAccessToken(), httpServletResponse); // 응답 헤더에 Access Token 저장
//        jwtUtil.addRefreshTokenToCookie(response.getRefreshToken(), httpServletResponse, true, null); // 응답 헤더에 Access Token 저장
//
//        return ResponseEntity.ok(ApiResponse.createSuccess(response));
//    }

}
