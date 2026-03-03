package com.project.demo.domain.user.service;

import com.project.demo.common.exception.auth.*;
import com.project.demo.common.exception.user.InValidNewPasswordException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.oauth2.SocialType;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.PasswordUpdateRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.dto.response.GetUserResponse;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.TokensResponse;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.entity.RefreshToken;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.RefreshTokenRepository;
import com.project.demo.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.ArrayList;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyLong;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * UserServiceImpl 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private SignUpRequest signUpRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // ADMIN_TOKEN 설정
        ReflectionTestUtils.setField(userService, "ADMIN_TOKEN", "admin-secret-token");

        // 테스트용 User 생성
        testUser = User.builder()
                .id(1L)
                .password("encodedPassword")
                .name("테스트 사용자")
                .isDeleted(false)
                .withdrawalAt(null)
                .userRole(UserRole.ROLE_USER)
                .socialType(SocialType.LOCAL)
                .socialId(null)
                .email("test@example.com")
                .profileImage("")
                .orders(new ArrayList<>())
                .userStocks(new ArrayList<>())
                .build();

        // 테스트용 SignUpRequest
        signUpRequest = new SignUpRequest(
                "password123!",
                "테스트 사용자",
                "test@example.com",
                "ROLE_USER",
                "");

        // 테스트용 LoginRequest
        loginRequest = new LoginRequest("test@example.com", "password123!");

        // 테스트용 Portfolio 생성
        Portfolio testPortfolio = Portfolio.builder()
                .id(1L)
                .balance(10000000L)
                .stockAsset(0L)
                .totalAsset(10000000L)
                .holdCount(0L)
                .totalQuantity(0L)
                .user(testUser)
                .userStocks(new ArrayList<>())
                .build();
        lenient().when(portfolioRepository.findByUser(any(User.class))).thenReturn(Optional.of(testPortfolio));
        lenient().when(portfolioRepository.findByUserId(anyLong())).thenReturn(Optional.of(testPortfolio));
    }

    @Test
    void 회원가입_성공_테스트() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByName(anyString())).thenReturn(false);
        when(portfolioRepository.findByUser(any(User.class))).thenReturn(Optional.empty()); // 신규 가입 시 포트폴리오 없음
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        // save()가 호출될 때 전달된 User 객체에 id를 설정하고 반환
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mock(RefreshToken.class));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(mock(Portfolio.class));

        // When
        LoginResponse response = userService.signUp(signUpRequest);

        // Then
        assertNotNull(response);
        assertNotNull(response.getUserId());
        assertEquals(testUser.getEmail(), response.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
        verify(portfolioRepository, times(1)).save(any(Portfolio.class));
    }

    @Test
    void 회원가입_이메일_중복_예외_테스트() {
        // Given
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));

        // When & Then
        assertThrows(DuplicateEmailException.class, () -> {
            userService.signUp(signUpRequest);
        });
    }

    @Test
    void 회원가입_닉네임_중복_예외_테스트() {
        // Given
        // 닉네임 중복 체크가 먼저 실행되므로 existsByEmail은 호출되지 않음
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByName(anyString())).thenReturn(true);
        when(userRepository.findByName(anyString())).thenReturn(Optional.of(testUser));

        // When & Then
        assertThrows(DuplicateNameException.class, () -> {
            userService.signUp(signUpRequest);
        });

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 로그인_성공_테스트() {
        // Given
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mock(RefreshToken.class));

        // When
        LoginResponse response = userService.login(loginRequest);

        // Then
        assertNotNull(response);
        assertEquals(testUser.getId(), response.getUserId());
        assertEquals(testUser.getEmail(), response.getEmail());
    }

    @Test
    void 로그인_사용자_없음_예외_테스트() {
        // Given
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundUserException.class, () -> {
            userService.login(loginRequest);
        });
    }

    @Test
    void 로그인_비밀번호_불일치_예외_테스트() {
        // Given
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // When & Then
        assertThrows(IncorrectPasswordException.class, () -> {
            userService.login(loginRequest);
        });
    }

    @Test
    void 로그아웃_테스트() {
        // Given
        Long userId = 1L;

        // When
        userService.logout(userId);

        // Then
        verify(refreshTokenRepository, times(1)).deleteById(userId);
    }

    @Test
    void 회원탈퇴_테스트() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        String result = userService.deleteUser(userId);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(String.valueOf(userId)));
        verify(refreshTokenRepository, times(1)).deleteById(userId);
        assertTrue(testUser.isDeleted());
    }

    @Test
    void 비밀번호_변경_성공_테스트() {
        // Given
        AuthUser authUser = new AuthUser(1L, "test@example.com", UserRole.ROLE_USER, "테스트");
        PasswordUpdateRequest request = new PasswordUpdateRequest(
                "oldPassword",
                "newPassword123!",
                "newPassword123!");

        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123!")).thenReturn("newEncodedPassword");

        // When
        String result = userService.updatePassword(authUser, request);

        // Then
        assertNotNull(result);
        assertTrue(result.contains(String.valueOf(testUser.getId())));
    }

    @Test
    void 비밀번호_변경_새_비밀번호_불일치_예외_테스트() {
        // Given
        AuthUser authUser = new AuthUser(1L, "test@example.com", UserRole.ROLE_USER, "테스트");
        PasswordUpdateRequest request = new PasswordUpdateRequest(
                "oldPassword",
                "newPassword123!",
                "differentPassword");

        // When & Then
        assertThrows(NewPasswordMismatch.class, () -> {
            userService.updatePassword(authUser, request);
        });
    }

    @Test
    void 비밀번호_변경_기존_비밀번호_불일치_예외_테스트() {
        // Given
        AuthUser authUser = new AuthUser(1L, "test@example.com", UserRole.ROLE_USER, "테스트");
        PasswordUpdateRequest request = new PasswordUpdateRequest(
                "wrongPassword",
                "newPassword123!",
                "newPassword123!");

        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        // When & Then
        assertThrows(IncorrectPasswordException.class, () -> {
            userService.updatePassword(authUser, request);
        });
    }

    @Test
    void 사용자_정보_조회_테스트() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        GetUserResponse response = userService.getUserInfo(userId);

        // Then
        assertNotNull(response);
        assertEquals(testUser.getId(), response.getUserId());
    }

    @Test
    void 사용자_정보_수정_테스트() {
        // Given
        Long userId = 1L;
        UpdateUserInfoRequest request = new UpdateUserInfoRequest("new@example.com", "새 이름");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        GetUserResponse response = userService.updateUserInfo(userId, request);

        // Then
        assertNotNull(response);
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void 이메일_중복_검증_테스트() {
        // Given
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // When
        boolean result = userService.validateDuplicateEmail("test@example.com");

        // Then
        assertTrue(result);
    }

    @Test
    void 이메일_중복_없음_검증_테스트() {
        // Given
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

        // When
        boolean result = userService.validateDuplicateEmail("new@example.com");

        // Then
        assertFalse(result);
    }

    @Test
    void Access_Token_재발급_토큰_유효하지_않음_예외_테스트() {
        // Given
        String refreshToken = "invalidRefreshToken";
        when(jwtUtil.validateToken(refreshToken)).thenReturn(false);

        // When & Then
        assertThrows(InvalidTokenException.class, () -> {
            userService.refreshAccessToken(refreshToken);
        });
    }

    @Test
    void 회원가입_탈퇴_사용자_복구_테스트() {
        // Given
        User deletedUser = User.createNewUser(
                "test@example.com",
                "탈퇴 사용자",
                "encodedPassword",
                UserRole.ROLE_USER,
                SocialType.LOCAL,
                "");
        ReflectionTestUtils.setField(deletedUser, "id", 1L);
        deletedUser.updateIsDeleted(); // 탈퇴 처리

        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(deletedUser));
        when(userRepository.existsByName(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mock(RefreshToken.class));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(mock(Portfolio.class));

        // When
        LoginResponse response = userService.signUp(signUpRequest);

        // Then
        assertNotNull(response);
        assertFalse(deletedUser.isDeleted()); // 복구 확인
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void 관리자_토큰_검증_성공_테스트() {
        // Given
        SignUpRequest adminRequest = new SignUpRequest(
                "password123!",
                "관리자",
                "admin@example.com",
                "ROLE_ADMIN",
                "admin-secret-token");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByName(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mock(RefreshToken.class));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(mock(Portfolio.class));

        // When
        LoginResponse response = userService.signUp(adminRequest);

        // Then
        assertNotNull(response);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void 관리자_토큰_검증_실패_예외_테스트() {
        // Given
        SignUpRequest adminRequest = new SignUpRequest(
                "password123!",
                "관리자",
                "admin@example.com",
                "ROLE_ADMIN",
                "wrong-token");
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByName(anyString())).thenReturn(false);

        // When & Then
        assertThrows(InvalidAdminTokenException.class, () -> {
            userService.signUp(adminRequest);
        });
    }

    @Test
    void 사용자_정보_조회_사용자_없음_예외_테스트() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundUserException.class, () -> {
            userService.getUserInfo(userId);
        });
    }

    @Test
    void 사용자_정보_수정_사용자_없음_예외_테스트() {
        // Given
        Long userId = 999L;
        UpdateUserInfoRequest request = new UpdateUserInfoRequest("new@example.com", "새 이름");
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundUserException.class, () -> {
            userService.updateUserInfo(userId, request);
        });
    }

    @Test
    void 회원탈퇴_사용자_없음_예외_테스트() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundUserException.class, () -> {
            userService.deleteUser(userId);
        });
    }

    @Test
    void 비밀번호_변경_새_비밀번호_기존과_동일_예외_테스트() {
        // Given
        AuthUser authUser = new AuthUser(1L, "test@example.com", UserRole.ROLE_USER, "테스트");
        PasswordUpdateRequest request = new PasswordUpdateRequest(
                "oldPassword",
                "oldPassword", // 새 비밀번호가 기존과 동일
                "oldPassword");

        when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword", "encodedPassword")).thenReturn(true);
        lenient().when(passwordEncoder.encode("oldPassword")).thenReturn("encodedPassword");

        // When & Then
        assertThrows(InValidNewPasswordException.class, () -> {
            userService.updatePassword(authUser, request);
        });
    }

    @Test
    void 로그인_탈퇴_사용자_예외_테스트() {
        // Given
        User deletedUser = User.createNewUser(
                "test@example.com",
                "탈퇴 사용자",
                "encodedPassword",
                UserRole.ROLE_USER,
                SocialType.LOCAL,
                "");
        ReflectionTestUtils.setField(deletedUser, "id", 1L);
        deletedUser.updateIsDeleted(); // 탈퇴 처리

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(deletedUser));

        // When & Then
        assertThrows(NotFoundUserException.class, () -> {
            userService.login(loginRequest);
        });
    }

    @Test
    void 닉네임_중복_검증_탈퇴_사용자_허용_테스트() {
        // Given
        User deletedUser = User.createNewUser(
                "other@example.com",
                "기존 닉네임",
                "encodedPassword",
                UserRole.ROLE_USER,
                SocialType.LOCAL,
                "");
        deletedUser.updateIsDeleted(); // 탈퇴 처리

        when(userRepository.existsByName("기존 닉네임")).thenReturn(true);
        when(userRepository.findByName("기존 닉네임")).thenReturn(Optional.of(deletedUser));

        // When - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            userService.validateDuplicateName("기존 닉네임");
        });
    }

    @Test
    void 닉네임_중복_검증_활성_사용자_예외_테스트() {
        // Given
        when(userRepository.existsByName("기존 닉네임")).thenReturn(true);
        when(userRepository.findByName("기존 닉네임")).thenReturn(Optional.of(testUser));

        // When & Then
        assertThrows(DuplicateNameException.class, () -> {
            userService.validateDuplicateName("기존 닉네임");
        });
    }

    @Test
    void 사용자_정보_수정_성공_테스트() {
        // Given
        Long userId = 1L;
        UpdateUserInfoRequest request = new UpdateUserInfoRequest("new@example.com", "새 이름");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        GetUserResponse response = userService.updateUserInfo(userId, request);

        // Then
        assertNotNull(response);
        assertEquals("new@example.com", testUser.getEmail());
        assertEquals("새 이름", testUser.getName());
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    void 회원가입_기본_역할_설정_테스트() {
        // Given
        SignUpRequest requestWithEmptyRole = new SignUpRequest(
                "password123!",
                "테스트 사용자",
                "new@example.com",
                "", // 빈 역할
                "");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByName(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(mock(RefreshToken.class));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(mock(Portfolio.class));

        // When
        LoginResponse response = userService.signUp(requestWithEmptyRole);

        // Then
        assertNotNull(response);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void 토큰_발급_테스트() {
        // Given
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            return token;
        });

        // When
        TokensResponse tokens = userService.issueTokens(testUser);

        // Then
        assertNotNull(tokens);
        assertEquals("Bearer accessToken", tokens.getAccessToken());
        assertEquals("refreshToken", tokens.getRefreshToken());
        verify(jwtUtil, times(1)).createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString());
        verify(jwtUtil, times(1)).createRefreshToken(anyLong());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    void isValid_토큰_일치_테스트() {
        // Given
        Long userId = 1L;
        String refreshToken = "validRefreshToken";
        RefreshToken existingToken = RefreshToken.builder()
                .key(userId)
                .value(refreshToken)
                .build();

        when(refreshTokenRepository.findById(userId)).thenReturn(Optional.of(existingToken));

        // When - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            userService.isValid(userId, refreshToken);
        });
    }

    @Test
    void isValid_토큰_불일치_예외_테스트() {
        // Given
        Long userId = 1L;
        String refreshToken = "validRefreshToken";
        String differentToken = "differentToken";
        RefreshToken existingToken = RefreshToken.builder()
                .key(userId)
                .value(differentToken)
                .build();

        when(refreshTokenRepository.findById(userId)).thenReturn(Optional.of(existingToken));

        // When & Then
        assertThrows(InvalidTokenException.class, () -> {
            userService.isValid(userId, refreshToken);
        });
    }

    @Test
    void 비밀번호_검증_성공_테스트() {
        // Given
        String inputPassword = "password123!";
        String correctPassword = "encodedPassword";
        when(passwordEncoder.matches(inputPassword, correctPassword)).thenReturn(true);

        // When - 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> {
            userService.validateCorrectPassword(inputPassword, correctPassword);
        });
    }

    @Test
    void 비밀번호_검증_실패_예외_테스트() {
        // Given
        String inputPassword = "wrongPassword";
        String correctPassword = "encodedPassword";
        when(passwordEncoder.matches(inputPassword, correctPassword)).thenReturn(false);

        // When & Then
        assertThrows(IncorrectPasswordException.class, () -> {
            userService.validateCorrectPassword(inputPassword, correctPassword);
        });
    }

    @Test
    void issueTokens_기존_토큰_삭제_테스트() {
        // Given
        when(jwtUtil.createAccessToken(anyLong(), anyString(), any(UserRole.class), anyString()))
                .thenReturn("Bearer accessToken");
        when(jwtUtil.createRefreshToken(anyLong())).thenReturn("refreshToken");
        doNothing().when(refreshTokenRepository).deleteById(1L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        TokensResponse tokens = userService.issueTokens(testUser);

        // Then
        assertNotNull(tokens);
        verify(refreshTokenRepository, times(1)).deleteById(1L);
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }
}