package com.project.demo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.auth.IncorrectPasswordException;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.common.oauth2.SocialType;
import com.project.demo.domain.user.repository.UserRepository;
import com.project.demo.domain.user.repository.RefreshTokenRepository;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.user.service.UserService;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * User 관련 통합 테스트
 * Testcontainers를 사용하여 실제 MySQL과 Redis 컨테이너로 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import(TestConfig.class)
@DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class UserIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserService userService;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private RefreshTokenRepository refreshTokenRepository;

        @Autowired
        private PortfolioRepository portfolioRepository;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private UserStockRepository userStockRepository;

        @Autowired
        private ExecutionRepository executionRepository;

        @PersistenceContext
        private EntityManager entityManager;

        @BeforeEach
        void setUp() {
                // MockMvc는 별도 스레드에서 실행되므로 @Transactional이 제대로 작동하지 않음
                // 따라서 각 테스트 전에 데이터를 수동으로 정리해야 함
                cleanupDatabase();
        }

        @Commit
        void cleanupDatabase() {
                // 외래키 제약조건 때문에 순서대로 삭제
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
                executionRepository.deleteAll();
                orderRepository.deleteAll();
                userStockRepository.deleteAll();
                portfolioRepository.deleteAll();
                refreshTokenRepository.deleteAll();
                userRepository.deleteAll();
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
                entityManager.flush();
                entityManager.clear();
        }

        @Test
        void 회원가입_후_로그인_테스트() throws Exception {
                // Given - 회원가입
                SignUpRequest signUpRequest = new SignUpRequest(
                                "Test123!@#",
                                "테스트 사용자",
                                "test@example.com",
                                null,
                                "");

                // When - 회원가입
                mockMvc.perform(post("/api/v1/users/sign-up")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signUpRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.accessToken").exists())
                                .andExpect(jsonPath("$.data.refreshToken").exists());

                // 데이터베이스에 저장되었는지 확인
                User savedUser = userRepository.findByEmail("test@example.com")
                                .orElseThrow();
                assertNotNull(savedUser);
                assertEquals("테스트 사용자", savedUser.getName());
                assertEquals("test@example.com", savedUser.getEmail());
                assertEquals(UserRole.ROLE_USER, savedUser.getUserRole());
                assertTrue(passwordEncoder.matches("Test123!@#", savedUser.getPassword()));

                // Then - 회원가입한 사용자로 로그인
                LoginRequest loginRequest = new LoginRequest("test@example.com",
                                "Test123!@#");
                mockMvc.perform(post("/api/v1/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.accessToken").exists())
                                .andExpect(jsonPath("$.data.refreshToken").exists());
        }

        @Test
        void 회원탈퇴_통합_테스트() throws Exception {
                // Given - 사용자 생성 및 데이터 저장
                User user = User.builder()
                                .email("delete@example.com")
                                .password(passwordEncoder.encode("Test123!@#"))
                                .name("탈퇴테스트")
                                .userRole(UserRole.ROLE_USER)
                                .socialType(SocialType.LOCAL)
                                .build();
                user = userRepository.save(user);
                Long userId = user.getId();

                // When - 탈퇴 요청 (애플리케이션 컨텍스트에서 인증 정보를 처리한다고 가정하거나 직접 ID 호출)
                // 실제로는 세션 기반이겠지만 테스트에서는 서비스 직접 호출 혹은 컨트롤러 권한 처리 확인
                userService.deleteUser(userId);

                // Then - DB에서 영구 삭제되었는지 확인
                assertFalse(userRepository.findById(userId).isPresent());
                // 연관 리프레시 토큰도 삭제되었는지 확인 (Repository 조회)
                assertTrue(refreshTokenRepository.findAllByUserId(userId).isEmpty());
        }

        @Test
        void 기존_사용자_로그인_테스트() throws Exception {
                // Given - 사용자 미리 생성
                User user = User.builder()
                                .email("login@example.com")
                                .password(passwordEncoder.encode("Test123!@#"))
                                .name("로그인 테스트")
                                .userRole(UserRole.ROLE_USER)
                                .socialType(SocialType.LOCAL)
                                .build();
                userRepository.save(user);

                LoginRequest request = new LoginRequest("login@example.com", "Test123!@#");

                // When & Then
                mockMvc.perform(post("/api/v1/users/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.accessToken").exists())
                                .andExpect(jsonPath("$.data.refreshToken").exists());
        }

        

}

