package com.project.demo.domain.user.controller;

import com.project.demo.domain.user.dto.request.PasswordUpdateRequest;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.dto.response.GetUserResponse;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.service.UserService;
import com.project.demo.domain.user.service.UserServiceImpl;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.TokensResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final JwtUtil jwtUtil;
    private final UserServiceImpl refreshTokenService;
    private final UserService userService;

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
    public ResponseEntity<ApiResponse<LoginResponse>> signUp(@Valid @RequestBody SignUpRequest signUpRequest, HttpServletResponse httpServletResponse) {
        LoginResponse response = userService.signUp(signUpRequest);

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
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse httpServletResponse) {
        LoginResponse response = userService.login(loginRequest);

        jwtUtil.addAccessTokenToHeader(response.getAccessToken(), httpServletResponse); // 응답 헤더에 Access Token 저장
        jwtUtil.addRefreshTokenToCookie(response.getRefreshToken(), httpServletResponse, true, null); // // 응답 쿠키에 Refresh Token 저장

        return ResponseEntity.ok(ApiResponse.createdSuccess(response)); // 201 코드
    }

    /**
     * 로그아웃 API
     * @param authUser
     * @return [로그아웃 성공 문구]
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(@AuthenticationPrincipal AuthUser authUser) {
        // 리프레시 토큰 삭제
        userService.logout(authUser.getUserId());
        log.info("로그아웃 성공!");
        return ResponseEntity.ok(ApiResponse.requestSuccess("로그아웃되었습니다."));
    }

    /**
     * 회원 탈퇴 API(SOft Delete)
     * @param userId
     * @param httpServletResponse
     * @return 회원 탈퇴 성공 문구
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable Long userId, HttpServletResponse httpServletResponse) {
        String response = userService.deleteUser(userId);
        jwtUtil.clearRefreshTokenCookie(httpServletResponse, true, null);

        return ResponseEntity.ok(ApiResponse.requestSuccess(response)); // 200 코드
    }

    /**
     * 비밀번호 변경 API
     * @param authUser
     * @param passwordUpdateRequest
     * @return [비밀번호 변경 성공 문구]
     */
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<String>> updatePassword(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody @Valid PasswordUpdateRequest passwordUpdateRequest) {

        String response = userService.updatePassword(authUser, passwordUpdateRequest);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response)); // 200 코드
    }

    /**
     * 유저 개인정보 조회 API
     * @param userId
     * @return [유저 이메일, 유저 이름, 유저의 잔액]
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<GetUserResponse>> getUser(@PathVariable Long userId) {
        GetUserResponse response = userService.getUserInfo(userId);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response)); // 200  코드
    }

    /**
     * 유저 개인정보 수정 API
     * @param userId
     * @return [유저 이메일, 유저 이름, 유저의 잔액]
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<ApiResponse<GetUserResponse>> updateUser(@PathVariable Long userId, @RequestBody UpdateUserInfoRequest updateUserInfoRequest) {
        GetUserResponse response = userService.updateUserInfo(userId, updateUserInfoRequest);
        return ResponseEntity.ok(ApiResponse.requestSuccess(response)); // 200  코드
    }

    /**
     * Access Token 재발급 API
     * @return [Access Token]
     */
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<String>> refreshAccessToken(HttpServletRequest httpServletRequest) {

        String refreshToken = jwtUtil.getRefreshTokenFromCookie(httpServletRequest); // 쿠키에서 Refresh Token 꺼내기
        String newAccessToken = refreshTokenService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.requestSuccess(newAccessToken));
    }
}