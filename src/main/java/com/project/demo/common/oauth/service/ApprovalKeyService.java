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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalKeyService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final KisApiAccessTokenService kisApiAccessTokenService;

    @Value("${kis.app.key}")
    private String appKey;

    @Value("${kis.app.secret}")
    private String appSecret;

    @Value("${kis.domain.virtual}")
    private String baseUrl;

    private String approvalKey;
    private LocalDateTime expiredAt;

    public synchronized String getApprovalKey() {
        if (approvalKey == null || LocalDateTime.now().isAfter(expiredAt)) {
            requestApprovalKey();
        }
        return approvalKey;
    }

    private void requestApprovalKey() {
        String url = baseUrl + "/oauth2/Approval";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "secretkey", appSecret
        );

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

        log.info("Approval Key 발급 완료: {}", response);
//        AesDecryptUtil.decrypt(response);

        this.approvalKey = (String) response.get("approval_key");

        String expired = (String) response.get("approval_key_expired");
    }
}




