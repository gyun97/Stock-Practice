package com.project.demo.common.jwt;

import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청이 들어올 때 JWT의 유효성 검증을 담당하는 커스텀 필터
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecurityFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpRequest,
            @NonNull HttpServletResponse httpResponse,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        // 클라이언트가 보낸 요청 헤더(Authorization: Bearer <JWT>)에서 토큰을 꺼내기
        String tokenValue = jwtUtil.getAccessTokenFromRequest(httpRequest);
        if (StringUtils.hasText(tokenValue)) {
            try {
                Claims claims = jwtUtil.extractClaims(tokenValue);
                Long userId = Long.parseLong(claims.getSubject());
                String email = claims.get("email", String.class);
                UserRole userRole = UserRole.of(claims.get("userRole", String.class));
                String name = claims.get("name", String.class);
                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    AuthUser authUser = new AuthUser(userId, email, userRole, name);
                    JwtAuthenticationToken authenticationToken = new JwtAuthenticationToken(authUser);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));

                    // 현재 요청을 보낸 사용자가 누구인지, 권한이 무엇인지를 저장
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken); // SecurityContextHolder는 현재 요청의 인증 상태(Authentication 객체)를 보관하는 “Thread-local 저장소”
                }
            } catch (ExpiredJwtException e) {
                log.error("만료된 JWT token 입니다.", e);
                handleJwtException(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Access Token expired");
            } catch (MalformedJwtException | SecurityException e) {
                log.error("유효하지 않는 JWT 서명 입니다.", e);
                handleJwtException(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT signature");
            } catch (UnsupportedJwtException e) {
                log.error("지원되지 않는 JWT 토큰 입니다.", e);
                handleJwtException(httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Unsupported JWT token");
            } catch (IllegalArgumentException e) {
                log.error("비어있거나 변형된 JWT 토큰 입니다.", e);
                handleJwtException(httpResponse, HttpServletResponse.SC_BAD_REQUEST, "Empty or malformed JWT");
            }

//            catch (SecurityException | MalformedJwtException e) {
//                log.error("Invalid JWT signature, 유효하지 않는 JWT 서명 입니다.", e);
//                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않는 JWT 서명입니다.");
//            } catch (ExpiredJwtException e) {
//                log.error("Expired JWT token, 만료된 JWT token 입니다.", e);
//                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "만료된 JWT 토큰입니다.");
//            } catch (UnsupportedJwtException e) {
//                log.error("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.", e);
//                httpResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "지원되지 않는 JWT 토큰입니다.");
//            } catch (Exception e) {
//                log.error("Internal server error", e);
//                httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//            }
        }

        chain.doFilter(httpRequest, httpResponse); // 토큰이 없으면 아무 검증도 하지 않고 다음 필터로 넘김(chain.doFilter)
    }

    /*
    공통 응답 헬퍼 메서드
     */
    // JWT는 Filter에서 처리되므로, 일반 @ControllerAdvice가 잡지 못해 필터 내부에서 직접 JSON 응답 내리는 방식 채택
    private void handleJwtException(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"status\": %d, \"error\": \"%s\"}", status, message);
        response.getWriter().write(json);
    }
}
