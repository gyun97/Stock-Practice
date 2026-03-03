package com.project.demo.common.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${kis.url.rest}")
    private String baseUrl;

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // 타임아웃 설정
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 연결 타임아웃: 10초
                .responseTimeout(Duration.ofSeconds(30)) // 응답 타임아웃: 30초
                .doOnConnected(conn -> conn
                        .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(30)) // 읽기 타임아웃: 30초
                        .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(30))); // 쓰기 타임아웃: 30초

        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
