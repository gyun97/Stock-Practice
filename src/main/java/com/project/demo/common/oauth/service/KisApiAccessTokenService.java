package com.project.demo.common.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiAccessTokenService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${kis.domain.virtual}")
    private String baseUrl;

    private String accessToken;
    private LocalDateTime expiredAt;

    public synchronized String getAccessToken() {
        if (accessToken == null || LocalDateTime.now().isAfter(expiredAt)) {
            requestAccessToken();
        }
        return accessToken;
    }

    private void requestAccessToken() {
        String url = baseUrl + "/oauth2/tokenP";

        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "appsecret", appSecret
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

        log.info("Access Token 발급 완료: {}", response);

        this.accessToken = (String) response.get("access_token");

        String expired = (String) response.get("access_token_token_expired");
        this.expiredAt = LocalDateTime.parse(expired, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
