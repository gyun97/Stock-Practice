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

    @Value("${kis.domain.real}")
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


//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.LocalDateTime;
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class KisApiAccessTokenService {
//
//    private final RestTemplate restTemplate = new RestTemplate();
//
//    @Value("${kis.app.key}")
//    private String appKey;
//
//    @Value("${kis.app.secret}")
//    private String appSecret;
//
//    @Value("${kis.domain.real}")
//    private String baseUrl;
//
//    private String accessToken;
//    private LocalDateTime expireAt;
//
//    public synchronized String getAccessToken() {
//        if (accessToken == null || expireAt.isBefore(LocalDateTime.now())) {
//            log.info("새로 Access Token을 발급합니다");
//            requestAccessToken();
//        } else {
//            log.info("기존 Access Token이 존재합니다.");
//        }
//
//        return accessToken;
//    }
//
//    private void requestAccessToken() {
//        // API 호출
//        Map<String, Object> response = restTemplate.postForObject(
//                "https://openapivts.koreainvestment.com:29443/oauth2/tokenP",
//                requestBody(),
//                Map.class
//        );
//
//        this.accessToken = (String) response.get("access_token");
//        int expiresIn = Integer.parseInt(response.get("expires_in").toString()); // 초 단위
//        this.expireAt = LocalDateTime.now().plusSeconds(expiresIn - 30); // 안전하게 30초 뺌
//    }
//
//    private Map<String, String> requestBody() {
//        return Map.of(
//                "grant_type", "client_credentials",
//                "appkey", appKey,
//                "appsecret", appSecret
//        );
//    }
//}


//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import java.time.Duration;
//import java.util.Map;
//
//@Service
//@RequiredArgsConstructor
//public class KisApiAccessTokenService {
//
//    private final RestTemplate restTemplate = new RestTemplate();
////    private final RedisTemplate<String, String> redisTemplate;
//
//    @Value("${kis.app.key}")
//    private String appKey;
//
//    @Value("${kis.app.secret}")
//    private String appSecret;
//
//    private static final String REDIS_KEY = "kis:accessToken";
//
//    public synchronized String getAccessToken() {
////        String token = redisTemplate.opsForValue().get(REDIS_KEY);
////        if (token != null) return token;
//
//        // 발급
//        Map<String, Object> response = restTemplate.postForObject(
//                "https://openapivts.koreainvestment.com:29443/oauth2/tokenP",
//                Map.of(
//                        "grant_type", "client_credentials",
//                        "appkey", appKey,
//                        "appsecret", appSecret
//                ),
//                Map.class
//        );
//
//        String accessToken = (String) response.get("access_token");
//        int expiresIn = Integer.parseInt(response.get("expires_in").toString());
//
//        // Redis에 저장 (만료시간 세팅)
////        redisTemplate.opsForValue().set(REDIS_KEY, accessToken, Duration.ofSeconds(expiresIn - 60));
//
//        return accessToken;
//    }
//}