package com.project.demo.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트를 위한 공통 설정
 * Testcontainers를 사용하여 MySQL과 Redis 컨테이너를 실행
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    private static final String USERNAME = "root";
    private static final String PASSWORD = "1234";
    private static final String DATABASE_NAME = "stock_test";
    
    // JWT Secret Key는 환경 변수에서 읽거나 기본값 사용 (Base64 인코딩된 테스트용 키)
    private static final String DEFAULT_JWT_SECRET_KEY = "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHk=";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withReuse(false)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait
                    .forLogMessage(".*ready for connections.*", 1))
            .withStartupTimeout(java.time.Duration.ofMinutes(2));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort())
            .withStartupTimeout(java.time.Duration.ofSeconds(30));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL 설정
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // Redis 설정
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
        registry.add("spring.redis.sentinel.master", () -> "");
        registry.add("spring.redis.sentinel.nodes", () -> "");

        // 환경 변수 설정
        registry.add("JWT_SECRET_KEY", () -> 
            System.getenv().getOrDefault("JWT_SECRET_KEY", DEFAULT_JWT_SECRET_KEY));
        registry.add("MYSQL_HOST", () -> "localhost");
        registry.add("MYSQL_DATABASE", () -> DATABASE_NAME);
        registry.add("MYSQL_NAME", () -> USERNAME);
        registry.add("MYSQL_PASSWORD", () -> PASSWORD);
        registry.add("REDIS_SENTINEL_PORT1", () -> "26379");
        registry.add("REDIS_SENTINEL_PORT2", () -> "26380");
        registry.add("REDIS_SENTINEL_PORT3", () -> "26381");
        registry.add("KIS_APP_KEY", () -> "test-key");
        registry.add("KIS_APP_SECRET", () -> "test-secret");
        registry.add("REAL_BASE_URL", () -> "https://test-api.com");
        registry.add("VIRTUAL_BASE_URL", () -> "https://test-api.com");
        registry.add("KAKAO_CLIENT_ID", () -> "test-kakao-id");
        registry.add("KAKAO_CLIENT_SECRET", () -> "test-kakao-secret");
        registry.add("KAKAO_REDIRECT_URL", () -> "http://localhost:8080/login/oauth2/code/kakao");
        registry.add("NAVER_CLIENT_ID", () -> "test-naver-id");
        registry.add("NAVER_CLIENT_SECRET", () -> "test-naver-secret");
        registry.add("NAVER_REDIRECT_URL", () -> "http://localhost:8080/login/oauth2/code/naver");
        registry.add("FRONTEND_URL", () -> "http://localhost:3000");
        registry.add("ADMIN_TOKEN", () -> "test-admin-token");
    }
}

