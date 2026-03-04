package com.project.demo.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.PasswordUpdateRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.dto.response.GetUserResponse;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.TokensResponse;
import com.project.demo.domain.user.entity.AuthUser;

import com.project.demo.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 단위 테스트
 */
@WebMvcTest(UserController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
                "cookie.secure=false",
                "cookie.domain=localhost"
})
class UserControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private UserService userService;

        @MockitoBean
        private JwtUtil jwtUtil;

        @Autowired
        private ObjectMapper objectMapper;

        private SignUpRequest signUpRequest;
        private LoginRequest loginRequest;
        private LoginResponse loginResponse;

        @BeforeEach
        void setUp() {
                signUpRequest = new SignUpRequest(
                                "password123!",
                                "테스트 사용자",
                                "test@example.com",
                                "ROLE_USER",
                                "");

                loginRequest = new LoginRequest("test@example.com", "password123!");

                loginResponse = LoginResponse.builder()
                                .accessToken("Bearer accessToken")
                                .refreshToken("refreshToken")
                                .userId(1L)
                                .email("test@example.com")
                                .name("테스트 사용자")
                                .build();

        }

    @Test
    void 회원가입_API_테스트() throws Exception {
        // Given
        when(userService.signUp(any(SignUpRequest.class))).thenReturn(loginResponse);
        doNothing().when(jwtUtil).addAccessTokenToHeader(anyString(), any());
        doNothing().when(jwtUtil).addRefreshTokenToCookie(anyString(), any(), anyBoolean(), anyString());

        // When & Then
        mockMvc.perform(post("/api/v1/users/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L));

        verify(userService, times(1)).signUp(any(SignUpRequest.class));
        verify(jwtUtil, times(1)).addAccessTokenToHeader(anyString(), any());
        verify(jwtUtil, times(1)).addRefreshTokenToCookie(anyString(), any(), anyBoolean(), anyString());
    }

    @Test
    void 로그인_API_테스트() throws Exception {
        // Given
        when(userService.login(any(LoginRequest.class))).thenReturn(loginResponse);
        doNothing().when(jwtUtil).addAccessTokenToHeader(anyString(), any());
        doNothing().when(jwtUtil).addRefreshTokenToCookie(anyString(), any(), anyBoolean(), anyString());

        // When & Then
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1L));

        verify(userService, times(1)).login(any(LoginRequest.class));
        verify(jwtUtil, times(1)).addAccessTokenToHeader(anyString(), any());
        verify(jwtUtil, times(1)).addRefreshTokenToCookie(anyString(), any(), anyBoolean(), anyString());
    }

        @Test
        void 회원탈퇴_API_테스트() throws Exception {
                // Given
                Long userId = 1L;
                when(userService.deleteUser(userId)).thenReturn("PK ID " + userId + "인 유저가 탈퇴처리되었습니다.");
                doNothing().when(jwtUtil).clearRefreshTokenCookie(any(), anyBoolean(), anyString());

                // When & Then
                mockMvc.perform(delete("/api/v1/users/{userId}", userId))
                                .andExpect(status().isOk());

                verify(userService, times(1)).deleteUser(userId);
                verify(jwtUtil, times(1)).clearRefreshTokenCookie(any(), anyBoolean(), anyString());
        }

        @Test
        void 비밀번호_변경_API_테스트() throws Exception {
                // Given
                PasswordUpdateRequest request = new PasswordUpdateRequest(
                                "oldPassword",
                                "newPassword123!",
                                "newPassword123!");
                when(userService.updatePassword(any(AuthUser.class), any(PasswordUpdateRequest.class)))
                                .thenReturn("PK ID 1인 유저의 비밀번호가 변경되었습니다.");

                // When & Then
                mockMvc.perform(patch("/api/v1/users/password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .principal(() -> "user"))
                                .andExpect(status().isOk());

                // @AuthenticationPrincipal은 Spring Security 설정이 필요하므로 실제로는 통합 테스트에서 검증
        }

        @Test
        void 사용자_정보_조회_API_테스트() throws Exception {
                // Given
                Long userId = 1L;
                GetUserResponse response = new GetUserResponse(
                                userId,
                                "테스트 사용자",
                                "test@example.com",
                                10000000.0);
                when(userService.getUserInfo(userId)).thenReturn(response);

                // When & Then
                mockMvc.perform(get("/api/v1/users/{userId}", userId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.userId").value(userId))
                                .andExpect(jsonPath("$.data.email").value("test@example.com"));

                verify(userService, times(1)).getUserInfo(userId);
        }

        @Test
        void 사용자_정보_수정_API_테스트() throws Exception {
                // Given
                Long userId = 1L;
                UpdateUserInfoRequest request = new UpdateUserInfoRequest("new@example.com", "새 이름");
                GetUserResponse response = new GetUserResponse(
                                userId,
                                "새 이름",
                                "new@example.com",
                                10000000.0);
                when(userService.updateUserInfo(anyLong(), any(UpdateUserInfoRequest.class))).thenReturn(response);

                // When & Then
                mockMvc.perform(patch("/api/v1/users/{userId}", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.email").value("new@example.com"));

                verify(userService, times(1)).updateUserInfo(anyLong(), any(UpdateUserInfoRequest.class));
        }

        @Test
        void Access_Token_재발급_API_테스트() throws Exception {
                // Given
                TokensResponse tokensResponse = new TokensResponse("Bearer newAccessToken", "newRefreshToken");
                when(jwtUtil.getRefreshTokenFromCookie(any())).thenReturn("refreshToken");
                when(userService.refreshAccessToken("refreshToken")).thenReturn(tokensResponse);
                doNothing().when(jwtUtil).addRefreshTokenToCookie(anyString(), any(), anyBoolean(), anyString());

                // When & Then
                mockMvc.perform(post("/api/v1/users/reissue"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").value("Bearer newAccessToken"));

                verify(jwtUtil, times(1)).getRefreshTokenFromCookie(any());
                verify(userService, times(1)).refreshAccessToken("refreshToken");
                verify(jwtUtil, times(1)).addRefreshTokenToCookie(eq("newRefreshToken"), any(), anyBoolean(),
                                anyString());
        }

    @Test
    void 회원가입_이메일_중복_예외_테스트() throws Exception {
        // Given
        when(userService.signUp(any(SignUpRequest.class)))
                .thenThrow(new com.project.demo.common.exception.auth.DuplicateEmailException());

        // When & Then
        mockMvc.perform(post("/api/v1/users/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signUpRequest)))
                .andExpect(status().isConflict());

        verify(userService, times(1)).signUp(any(SignUpRequest.class));
    }

    @Test
    void 로그인_사용자_없음_예외_테스트() throws Exception {
        // Given
        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new com.project.demo.common.exception.user.NotFoundUserException());

        // When & Then
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isNotFound());

        verify(userService, times(1)).login(any(LoginRequest.class));
    }

        @Test
        void 사용자_정보_조회_사용자_없음_예외_테스트() throws Exception {
                // Given
                Long userId = 999L;
                when(userService.getUserInfo(userId))
                                .thenThrow(new com.project.demo.common.exception.user.NotFoundUserException());

                // When & Then
                mockMvc.perform(get("/api/v1/users/{userId}", userId))
                                .andExpect(status().isNotFound());

                verify(userService, times(1)).getUserInfo(userId);
        }

        @Test
        void 회원가입_유효하지_않은_요청_테스트() throws Exception {
                // Given - 필수 필드 누락
                SignUpRequest invalidRequest = new SignUpRequest(
                                "", // 빈 비밀번호
                                "", // 빈 이름
                                "", // 빈 이메일
                                "",
                                "");

                // When & Then
                mockMvc.perform(post("/api/v1/users/sign-up")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                                .andExpect(status().isBadRequest());

                verify(userService, never()).signUp(any());
        }
}
