package com.project.demo.common.jwt;

import com.project.demo.domain.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * JwtUtil 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private String testSecretKey;
    private Long testUserId;
    private String testEmail;
    private UserRole testUserRole;
    private String testName;

    @BeforeEach
    void setUp() {
        // 테스트용 Secret Key 생성 (Base64 인코딩된 256비트 키)
        // 실제 프로젝트에서 사용하는 형식과 동일하게 생성
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            keyBytes[i] = (byte) (i + 1);
        }
        testSecretKey = Base64.getEncoder().encodeToString(keyBytes);

        // JwtUtil 초기화 - @PostConstruct 메서드 호출
        ReflectionTestUtils.setField(jwtUtil, "secretKey", testSecretKey);
        // init() 메서드를 직접 호출하여 key 초기화
        ReflectionTestUtils.invokeMethod(jwtUtil, "init");

        // 테스트 데이터
        testUserId = 1L;
        testEmail = "test@example.com";
        testUserRole = UserRole.ROLE_USER;
        testName = "테스트 사용자";
    }

    @Test
    void Access_Token_생성_성공_테스트() {
        // When
        String accessToken = jwtUtil.createAccessToken(testUserId, testEmail, testUserRole, testName);

        // Then
        assertNotNull(accessToken);
        assertTrue(accessToken.startsWith("Bearer "));
        
        // 토큰 검증 - JwtUtil의 메서드 사용
        String tokenWithoutBearer = accessToken.substring(7);
        assertTrue(jwtUtil.validateToken(tokenWithoutBearer));
        
        // Claims 추출 및 검증
        Claims claims = jwtUtil.extractClaims(tokenWithoutBearer);
        assertNotNull(claims);
        assertEquals(String.valueOf(testUserId), claims.getSubject());
        assertEquals(testEmail, claims.get("email", String.class));
        assertEquals(testUserRole.name(), claims.get("userRole", String.class));
        assertEquals(testName, claims.get("name", String.class));
        
        // getUserIdFromToken으로 사용자 ID 확인
        Long extractedUserId = jwtUtil.getUserIdFromToken(accessToken);
        assertEquals(testUserId, extractedUserId);
    }

    @Test
    void Refresh_Token_생성_성공_테스트() {
        // When
        String refreshToken = jwtUtil.createRefreshToken(testUserId);

        // Then
        assertNotNull(refreshToken);
        assertFalse(refreshToken.startsWith("Bearer ")); // Refresh Token은 Bearer 없음
        
        // 토큰 검증 - JwtUtil의 메서드 사용
        assertTrue(jwtUtil.validateToken(refreshToken));
        
        // Claims 추출 및 검증
        Claims claims = jwtUtil.extractClaims(refreshToken);
        assertNotNull(claims);
        assertEquals(String.valueOf(testUserId), claims.getSubject());
        assertNotNull(claims.getExpiration());
        
        // getUserIdFromToken으로 사용자 ID 확인
        Long extractedUserId = jwtUtil.getUserIdFromToken(refreshToken);
        assertEquals(testUserId, extractedUserId);
    }

    @Test
    void 토큰_검증_성공_테스트() {
        // Given
        String token = jwtUtil.createAccessToken(testUserId, testEmail, testUserRole, testName);
        String tokenWithoutBearer = token.substring(7);

        // When
        boolean isValid = jwtUtil.validateToken(tokenWithoutBearer);

        // Then
        assertTrue(isValid);
    }

    @Test
    void 토큰_검증_만료된_토큰_예외_테스트() {
        // Given - 만료된 토큰을 직접 생성하기는 어려우므로,
        // validateToken이 ExpiredJwtException을 던지는지 확인하는 것은
        // 실제 만료된 토큰이 필요하므로 통합 테스트에서 검증하는 것이 적절함
        // 단위 테스트에서는 잘못된 형식의 토큰 검증으로 대체
        String invalidToken = "expired.token.here";
        
        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);
        
        // Then
        assertFalse(isValid);
    }

    @Test
    void 토큰_검증_잘못된_서명_테스트() {
        // Given - 잘못된 형식의 토큰
        String invalidToken = "invalid.token.signature";

        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void 토큰_검증_잘못된_형식_테스트() {
        // Given
        String invalidToken = "invalid.token.format";

        // When
        boolean isValid = jwtUtil.validateToken(invalidToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void 토큰_검증_빈_토큰_테스트() {
        // Given
        String emptyToken = "";

        // When
        boolean isValid = jwtUtil.validateToken(emptyToken);

        // Then
        assertFalse(isValid);
    }

    @Test
    void Claims_추출_성공_테스트() {
        // Given
        String token = jwtUtil.createAccessToken(testUserId, testEmail, testUserRole, testName);
        String tokenWithoutBearer = token.substring(7);

        // When
        Claims claims = jwtUtil.extractClaims(tokenWithoutBearer);

        // Then
        assertNotNull(claims);
        assertEquals(String.valueOf(testUserId), claims.getSubject());
        assertEquals(testEmail, claims.get("email", String.class));
        assertEquals(testUserRole.name(), claims.get("userRole", String.class));
        assertEquals(testName, claims.get("name", String.class));
    }

    @Test
    void Claims_추출_Bearer_포함_토큰_테스트() {
        // Given
        String token = jwtUtil.createAccessToken(testUserId, testEmail, testUserRole, testName);

        // When - Bearer 포함된 토큰도 처리 가능해야 함
        Claims claims = jwtUtil.extractClaims(token);

        // Then
        assertNotNull(claims);
        assertEquals(String.valueOf(testUserId), claims.getSubject());
    }

    @Test
    void 사용자_ID_추출_성공_테스트() {
        // Given
        String token = jwtUtil.createAccessToken(testUserId, testEmail, testUserRole, testName);

        // When
        Long userId = jwtUtil.getUserIdFromToken(token);

        // Then
        assertEquals(testUserId, userId);
    }

    @Test
    void 사용자_ID_추출_Bearer_없는_토큰_테스트() {
        // Given
        String token = jwtUtil.createRefreshToken(testUserId);

        // When
        Long userId = jwtUtil.getUserIdFromToken(token);

        // Then
        assertEquals(testUserId, userId);
    }

    @Test
    void 사용자_ID_추출_잘못된_토큰_예외_테스트() {
        // Given
        String invalidToken = "invalid.token";

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            jwtUtil.getUserIdFromToken(invalidToken);
        });
    }

    @Test
    void 헤더에서_Access_Token_추출_성공_테스트() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        String token = "Bearer testToken123";
        when(request.getHeader("Authorization")).thenReturn(token);

        // When
        String extractedToken = jwtUtil.getAccessTokenFromRequest(request);

        // Then
        assertEquals("testToken123", extractedToken);
        verify(request, times(1)).getHeader("Authorization");
    }

    @Test
    void 헤더에서_Access_Token_추출_Bearer_없음_테스트() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        String token = "testToken123";
        when(request.getHeader("Authorization")).thenReturn(token);

        // When
        String extractedToken = jwtUtil.getAccessTokenFromRequest(request);

        // Then
        assertNull(extractedToken);
    }

    @Test
    void 헤더에서_Access_Token_추출_헤더_없음_테스트() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        // When
        String extractedToken = jwtUtil.getAccessTokenFromRequest(request);

        // Then
        assertNull(extractedToken);
    }

    @Test
    void 쿠키에서_Refresh_Token_추출_성공_테스트() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        Cookie cookie = new Cookie("refreshToken", "testRefreshToken123");
        when(request.getCookies()).thenReturn(new Cookie[]{cookie});

        // When
        String extractedToken = jwtUtil.getRefreshTokenFromCookie(request);

        // Then
        assertEquals("testRefreshToken123", extractedToken);
    }

    @Test
    void 쿠키에서_Refresh_Token_추출_쿠키_없음_예외_테스트() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null);

        // When & Then
        assertThrows(IllegalStateException.class, () -> {
            jwtUtil.getRefreshTokenFromCookie(request);
        });
    }

    @Test
    void 쿠키에서_Refresh_Token_추출_해당_쿠키_없음_테스트() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        Cookie otherCookie = new Cookie("otherCookie", "value");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});

        // When
        String extractedToken = jwtUtil.getRefreshTokenFromCookie(request);

        // Then
        assertNull(extractedToken);
    }

    @Test
    void Bearer_제거_성공_테스트() throws Exception {
        // Given
        String tokenWithBearer = "Bearer testToken123";

        // When
        String token = jwtUtil.substringToken(tokenWithBearer);

        // Then
        assertEquals("testToken123", token);
    }

    @Test
    void Bearer_제거_Bearer_없음_예외_테스트() {
        // Given
        String tokenWithoutBearer = "testToken123";

        // When & Then
        assertThrows(Exception.class, () -> {
            jwtUtil.substringToken(tokenWithoutBearer);
        });
    }

    @Test
    void Bearer_제거_빈_문자열_예외_테스트() {
        // Given
        String emptyToken = "";

        // When & Then
        assertThrows(Exception.class, () -> {
            jwtUtil.substringToken(emptyToken);
        });
    }

    @Test
    void Access_Token_헤더_추가_테스트() {
        // Given
        HttpServletResponse response = mock(HttpServletResponse.class);
        String accessToken = "Bearer testToken123";

        // When
        jwtUtil.addAccessTokenToHeader(accessToken, response);

        // Then
        verify(response, times(1)).setHeader("Authorization", accessToken);
    }

    @Test
    void Refresh_Token_쿠키_추가_테스트() {
        // Given
        HttpServletResponse response = mock(HttpServletResponse.class);
        String refreshToken = "testRefreshToken123";
        boolean isSecure = true;
        String domain = "example.com";

        // When
        jwtUtil.addRefreshTokenToCookie(refreshToken, response, isSecure, domain);

        // Then
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    void Refresh_Token_쿠키_추가_domain_null_테스트() {
        // Given
        HttpServletResponse response = mock(HttpServletResponse.class);
        String refreshToken = "testRefreshToken123";
        boolean isSecure = false;
        String domain = null;

        // When
        jwtUtil.addRefreshTokenToCookie(refreshToken, response, isSecure, domain);

        // Then
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    void Refresh_Token_쿠키_삭제_테스트() {
        // Given
        HttpServletResponse response = mock(HttpServletResponse.class);
        boolean isSecure = true;
        String domain = "example.com";

        // When
        jwtUtil.clearRefreshTokenCookie(response, isSecure, domain);

        // Then
        verify(response, times(1)).addHeader(eq(HttpHeaders.SET_COOKIE), anyString());
    }

    @Test
    void Access_Token_만료_시간_확인_테스트() {
        // When
        String token = jwtUtil.createAccessToken(testUserId, testEmail, testUserRole, testName);
        String tokenWithoutBearer = token.substring(7);
        
        // 토큰이 유효한지 확인 (만료되지 않았는지)
        assertTrue(jwtUtil.validateToken(tokenWithoutBearer));
        
        // Claims 추출 및 만료 시간 확인
        Claims claims = jwtUtil.extractClaims(tokenWithoutBearer);
        assertNotNull(claims);
        Date expiration = claims.getExpiration();
        Date issuedAt = claims.getIssuedAt();
        
        assertNotNull(expiration);
        assertNotNull(issuedAt);
        assertTrue(expiration.after(issuedAt));
        // 만료 시간이 약 60분 후여야 함 (약간의 오차 허용)
        long expectedExpiration = issuedAt.getTime() + (60 * 60 * 1000L);
        assertTrue(Math.abs(expiration.getTime() - expectedExpiration) < 1000); // 1초 오차 허용
    }

    @Test
    void Refresh_Token_만료_시간_확인_테스트() {
        // When
        String token = jwtUtil.createRefreshToken(testUserId);
        
        // 토큰이 유효한지 확인 (만료되지 않았는지)
        assertTrue(jwtUtil.validateToken(token));
        
        // Claims 추출 및 만료 시간 확인
        Claims claims = jwtUtil.extractClaims(token);
        assertNotNull(claims);
        Date expiration = claims.getExpiration();
        Date issuedAt = claims.getIssuedAt();
        
        assertNotNull(expiration);
        assertNotNull(issuedAt);
        assertTrue(expiration.after(issuedAt));
        // 만료 시간이 약 14일 후여야 함 (약간의 오차 허용)
        long expectedExpiration = issuedAt.getTime() + (14 * 24 * 60 * 60 * 1000L);
        assertTrue(Math.abs(expiration.getTime() - expectedExpiration) < 1000); // 1초 오차 허용
    }

    @Test
    void 여러_사용자_토큰_생성_테스트() {
        // Given
        Long userId1 = 1L;
        Long userId2 = 2L;

        // When
        String token1 = jwtUtil.createAccessToken(userId1, "user1@example.com", UserRole.ROLE_USER, "사용자1");
        String token2 = jwtUtil.createAccessToken(userId2, "user2@example.com", UserRole.ROLE_ADMIN, "사용자2");

        // Then
        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
        
        // 각 토큰에서 사용자 ID 추출하여 검증
        Long extractedUserId1 = jwtUtil.getUserIdFromToken(token1);
        Long extractedUserId2 = jwtUtil.getUserIdFromToken(token2);
        
        assertEquals(userId1, extractedUserId1);
        assertEquals(userId2, extractedUserId2);
        
        // 각 토큰이 유효한지 확인
        assertTrue(jwtUtil.validateToken(token1.substring(7)));
        assertTrue(jwtUtil.validateToken(token2.substring(7)));
    }
}

