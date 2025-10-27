package com.project.demo.common.jwt;


import com.project.demo.common.exception.auth.ExpiredTokenException;
import com.project.demo.domain.user.enums.UserRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.rmi.ServerException;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Slf4j(topic = "JwtUtil")
@Component
public class JwtUtil {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_KEY = "auth";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final long ACCESS_TOKEN_TIME = 60 * 60 * 1000L; // 60분
//    private static final long ACCESS_TOKEN_TIME = 1000L; // 1초(리프레쉬 토큰으로 인한 엑세스 토큰 재발급 시험)
    private static final long REFRESH_TOKEN_TIME = 14 * 24 * 60 * 60 * 1000L; // 14일
//    private static final long REFRESH_TOKEN_TIME = 1000L; // 14일
    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
    @Value("${JWT_SECRET_KEY}")
    private String secretKey;
    private Key key;

    @PostConstruct
    public void init() {
        log.info("Initializing JWT with secretKey: {}", secretKey);
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        key = Keys.hmacShaKeyFor(bytes);
    }

    /*
    Access Token 생성
     */
    public String createAccessToken(Long userId, String email, UserRole userRole, String name) {
        Date date = new Date();

        String newAccessToken = BEARER_PREFIX +
                Jwts.builder()
                        .setSubject(String.valueOf(userId))
                        .claim("email", email)
                        .claim("userRole", userRole)
                        .claim("name", name)
                        .setExpiration(new Date(date.getTime() + ACCESS_TOKEN_TIME))
                        .setIssuedAt(date)
                        .signWith(key, signatureAlgorithm)
                        .compact();

        log.info("Access Token 발급: {}", newAccessToken);
        return newAccessToken;
    }

    /*
    Refresh Token 생성
     */
    public String createRefreshToken(Long userId) {
        Date now = new Date();

        String refreshToken =
                Jwts.builder()
                        .setSubject(String.valueOf(userId))
                        .setIssuedAt(now)
                        .setExpiration(new Date(now.getTime() + REFRESH_TOKEN_TIME))
                        .signWith(key, signatureAlgorithm)
                        .compact();

        log.info("Refresh Token 발급: {}", refreshToken);

        return refreshToken;
    }

     /*
     Access Token을 응답 헤더에 추가
      */
    public void addAccessTokenToHeader(String accessToken, HttpServletResponse res) {
        res.setHeader(AUTHORIZATION_HEADER, accessToken);
    }

    /*
    Refresh Token을 HttpOnly 쿠키로 응답에 추가
     */
    public void addRefreshTokenToCookie(String refreshToken, HttpServletResponse response, boolean isSecure, String domain) {
//        refreshToken = refreshToken.replaceAll("\\s+", "");
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)                      // JS 접근 차단해서 XSS 위험 줄이기
                .secure(isSecure)                    // HTTPS일 때만 전송 (프로덕션 true 권장)
                .path("/")                           // 쿠키 전송 경로
                .maxAge(REFRESH_TOKEN_TIME)
                .sameSite("Strict");                 // CSRF 방지: 상황에 따라 "Lax" 또는 "None" 사용

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        ResponseCookie cookie = builder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /*
    Refresh Token 삭제 (로그아웃, 회원탈퇴 시 사용)
     */
    public void clearRefreshTokenCookie(HttpServletResponse response, boolean isSecure, String domain) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecure)
                .path("/")
                .maxAge(0)      // 즉시 만료
                .sameSite("Strict");

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }


    /*
    요청 헤더에서 Access Token 추출
     */
    public String getAccessTokenFromRequest(HttpServletRequest req) {
        String accessToken = req.getHeader(AUTHORIZATION_HEADER);

        if (accessToken != null && accessToken.startsWith(BEARER_PREFIX)) {
            // "Bearer " 제거 후 순수 토큰만 반환
            return accessToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /*
    쿠키에서 Refresh Token 추출
     */
    public String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) throw new IllegalStateException("Cookie가 없습니다.");

        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /*
    Bearer 제거하고 순수 토큰 값 추출
     */
    public String substringToken(String tokenValue) throws ServerException {
        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(BEARER_PREFIX)) {
            return tokenValue.substring(7);
        }
        throw new ServerException("Token not found");
    }

    /*
    토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()// JWT 파서를 생성할 준비
                    .setSigningKey(key) // 서명 검증에 사용할 비밀키 등록
                    .build()
                    .parseClaimsJws(token); // 실제 JWT 토큰을 파싱하고 서명 검증 수행
            return true;
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT token, 만료된 JWT token 입니다.");

            // 쿠키 제거와 함께 만료 메시지 반환
            throw new ExpiredTokenException();
        } catch (SecurityException | MalformedJwtException | SignatureException e) {
            log.error("Invalid JWT signature, 유효하지 않는 JWT 서명 입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        }
        return false;
    }

    /*
    Payload에서 유저 정보 추출
     */
    public Claims getUserInfoFromToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    /*
    토큰에서 사용자 ID 추출
     */
    public Long getUserIdFromToken(String token) {
        try {
            if (token != null && token.startsWith(BEARER_PREFIX)) {
                token = token.substring(BEARER_PREFIX.length());
            }
            
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            log.error("토큰에서 사용자 ID 추출 실패", e);
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }
    }

    /*
    토큰에서 Claim(데이터) 추출
     */
    public Claims extractClaims(String token) {
        if (token != null && token.startsWith(BEARER_PREFIX)) {
            token = token.substring(BEARER_PREFIX.length());
        }

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

