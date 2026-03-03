package com.project.demo.common.kis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KisApiAccessTokenService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final StringRedisTemplate redisTemplate;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${kis.url.rest}")
    private String baseUrl;

    private String accessToken;

    public synchronized String getAccessToken() {
        accessToken = redisTemplate.opsForValue().get("kis:access_token");
        if (accessToken == null) {
            requestAccessToken();
        } else {
            log.info("유효 기간이 지나지 않은 토큰 존재");
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
        redisTemplate.opsForValue()
                .set("kis:access_token", this.accessToken, Duration.ofHours(24));
    }
}
