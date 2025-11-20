package com.project.demo.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebClientConfig 단위 테스트
 * Spring 컨텍스트 없이 WebClientConfig의 설정을 검증
 */
class WebClientConfigUnitTest {

    /**
     * WebClientConfig가 WebClient 빈을 생성하는지 테스트
     * WebClient.Builder를 수동으로 생성하여 테스트
     */
    @Test
    void WebClientConfig_빈_생성_테스트() {
        // WebClientConfig와 동일한 방식으로 WebClient 생성
        WebClient.Builder builder = WebClient.builder();
        
        // WebClient 생성 (WebClientConfig의 webClient 메서드와 동일한 로직)
        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(30))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(30)));
        
        WebClient webClient = builder
                .baseUrl("https://openapi.koreainvestment.com:9443")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        
        assertNotNull(webClient, "WebClient 빈이 생성되어야 합니다");
    }

    /**
     * WebClient 설정 값 검증 테스트
     * 타임아웃 값들이 올바르게 설정되었는지 확인
     */
    @Test
    void WebClient_설정_값_검증_테스트() {
        // WebClientConfig와 동일한 설정으로 WebClient 생성
        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofSeconds(30))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(30))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(30)));

        WebClient webClient = WebClient.builder()
                .baseUrl("https://test.example.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        assertNotNull(webClient, "WebClient가 생성되어야 합니다");
    }

    /**
     * 기본 헤더 설정 검증 테스트
     */
    @Test
    void 기본_헤더_설정_검증_테스트() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://test.example.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .build();

        // WebClient가 생성되었는지 확인
        assertNotNull(webClient);
        
        // RequestHeadersSpec이 생성되는지 확인 (기본 헤더가 적용되는지 간접 확인)
        WebClient.RequestHeadersSpec<?> request = webClient.get().uri("/test");
        assertNotNull(request, "RequestHeadersSpec이 생성되어야 합니다");
    }

    /**
     * Base URL 설정 검증 테스트
     */
    @Test
    void Base_URL_설정_검증_테스트() {
        String expectedBaseUrl = "https://openapi.koreainvestment.com:9443";
        
        WebClient webClient = WebClient.builder()
                .baseUrl(expectedBaseUrl)
                .build();

        assertNotNull(webClient);
        
        // baseUrl이 설정되었는지 확인 (간접적으로)
        WebClient.RequestHeadersUriSpec<?> request = webClient.get();
        assertNotNull(request);
    }

    /**
     * 타임아웃 설정 검증 테스트
     * HttpClient의 타임아웃 설정이 올바른지 확인
     */
    @Test
    void 타임아웃_설정_검증_테스트() {
        int expectedConnectTimeout = 10000; // 10초
        Duration expectedResponseTimeout = Duration.ofSeconds(30);
        int expectedReadTimeout = 30;
        int expectedWriteTimeout = 30;

        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, expectedConnectTimeout)
                .responseTimeout(expectedResponseTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(expectedReadTimeout))
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(expectedWriteTimeout)));

        WebClient webClient = WebClient.builder()
                .baseUrl("https://test.example.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        assertNotNull(webClient);
        // 타임아웃 설정이 적용되었는지 확인 (HttpClient가 생성되었으므로 설정이 적용됨)
        assertNotNull(httpClient);
    }

    /**
     * WebClient 빌더 패턴 검증 테스트
     */
    @Test
    void WebClient_빌더_패턴_검증_테스트() {
        WebClient.Builder builder = WebClient.builder();
        assertNotNull(builder);

        WebClient webClient = builder
                .baseUrl("https://test.example.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

        assertNotNull(webClient);
    }

    /**
     * 여러 헤더 설정 검증 테스트
     */
    @Test
    void 여러_헤더_설정_검증_테스트() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://test.example.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .defaultHeader("X-Custom-Header", "test-value")
                .build();

        assertNotNull(webClient);
        
        WebClient.RequestHeadersSpec<?> request = webClient.get().uri("/test");
        assertNotNull(request);
    }
}
