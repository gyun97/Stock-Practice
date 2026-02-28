package com.project.demo.common.jwt;

import com.project.demo.common.oauth2.OAuth2LoginFailureHandler;
import com.project.demo.common.oauth2.OAuth2SuccessHandler;
import com.project.demo.common.oauth2.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity // Spring Security 기능을 전역적으로 활성화 (FilterChain 생성)
@EnableMethodSecurity(securedEnabled = true) // @Secured, @PreAuthorize 등 메서드 단위 권한 검사를 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtSecurityFilter jwtSecurityFilter; // 요청이 들어올 때 JWT의 유효성 검증을 담당하는 커스텀 필터
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;
    private final CustomOAuth2UserService customOAuth2UserService;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 활성화
                .csrf(AbstractHttpConfigurer::disable) // JWT 기반 인증은 Stateless(무상태) 이므로 CSRF 토큰이 불필요
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션을 생성하거나 저장하지 않음(모든 요청은 오직 JWT 토큰
                                                                                // 기반으로 인증)
                )
                .addFilterBefore(jwtSecurityFilter, SecurityContextHolderAwareRequestFilter.class) // SecurityContextHolderAwareRequestFilter
                                                                                                   // 이전에
                                                                                                   // JwtSecurityFilter를
                                                                                                   // 실행

                // 불필요한 필터 비활성화
                .formLogin(AbstractHttpConfigurer::disable) // 기본 로그인 폼 UI 비활성화(REST API는 JSON 로그인 사용)
                .anonymous(org.springframework.security.config.Customizer.withDefaults()) // 익명 사용자 허용 (permitAll 설정에
                                                                                          // 필요)
                .httpBasic(AbstractHttpConfigurer::disable) // Authorization 헤더로 Base64 로그인 비활성(JWT 사용 시 중복)
                .logout(AbstractHttpConfigurer::disable) // 세션 기반 로그아웃 처리 비활성화(JWT는 세션 없음 (토큰 만료로 처리))

                // URL 별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/users/login",
                                "/api/v1/users/sign-up",
                                "/api/v1/users/reissue", // 토큰 재발급 API (쿠키 기반 인증)
                                "/oauth2/authorization/**", // OAuth2 인증 경로
                                "/login/oauth2/code/**", // OAuth2 콜백 경로
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/v1/stocks/**",
                                "/api/v1/portfolios/ranking", // 랭킹 API는 공개
                                "/signup/**",
                                // WebSocket 관련 경로 추가
                                "/ws/**", // WebSocket 연결 경로
                                "/topic/**", // STOMP 토픽 구독 경로
                                "/app/**" // STOMP 메시지 전송 경로
                        )
                        .permitAll() // 해당 URL 인증 없이 접근 가능
                        .anyRequest().authenticated() // 위 조건 외 나머지 요청은 반드시 JWT 인증 필요
                )

                // OAuth 인증 처리
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler) // 동의하고 계속하기를 눌렀을 시 Handler
                        .failureHandler(oAuth2LoginFailureHandler)
                        .loginPage("/oauth2/authorization/kakao") // 카카오 로그인 페이지
                )
                .build();
    }

    /*
     * 브라우저 요청 시 Origin(출처) 허용 설정을 세부적으로 정의
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOriginPattern("*"); // 모든 오리진 허용
        configuration.addAllowedMethod("*"); // 모든 HTTP 메서드(GET, POST, PUT 등) 허용
        configuration.addAllowedHeader("*"); // 쿠키, 인증 헤더 포함 요청도 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}