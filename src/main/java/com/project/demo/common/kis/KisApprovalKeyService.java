package com.project.demo.common.kis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KisApprovalKeyService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${kis.url.rest}")
    private String baseUrl;

    private static final String KIS_APPROVAL_KEY = "kis:approval_key";

    public synchronized String getApprovalKey() {
        String approvalKey = redisTemplate.opsForValue().get(KIS_APPROVAL_KEY);
        if (approvalKey == null) {
            log.info("새로 Approval Key를 발급합니다");
            return requestApprovalKey();
        } else {
            log.info("기존 Approval Key가 존재합니다.");
            return approvalKey;
        }
    }

    /**
     * 기존 키를 즉시 만료시키고 새로운 키를 강제로 발급받습니다.
     * WebSocket 연결이 즉시 끊어지는 등 키가 의심스러울 때 사용합니다.
     */
    public synchronized String refreshApprovalKey() {
        log.info("Approval Key 강제 갱신 요청됨");
        redisTemplate.delete(KIS_APPROVAL_KEY);
        return getApprovalKey();
    }

    private String requestApprovalKey() {
        String url = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/oauth2/Approval";
        log.info("KIS Approval Key 발급 요청 중... URL: {}", url);
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    url,
                    requestBody(),
                    Map.class);

            if (response == null || response.get("approval_key") == null) {
                log.error("Approval Key 응답이 올바르지 않음: {}", response);
                throw new RuntimeException("Approval Key 발급 실패");
            }

            String approvalKey = (String) response.get("approval_key");
            log.info("approvalKey 발급 완료");
            
            // 하루짜리라서 안전하게 23시간으로 설정
            redisTemplate.opsForValue().set(KIS_APPROVAL_KEY, approvalKey, Duration.ofHours(23));
            return approvalKey;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("KIS Approval Key 발급 실패! URL: {}, 상태코드: {}, 응답바디: {}", 
                    url, e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("KIS Approval Key 발급 중 알 수 없는 오류 발생", e);
            throw e;
        }
    }

    private Map<String, String> requestBody() {
        return Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "secretkey", appSecret);
    }

}
