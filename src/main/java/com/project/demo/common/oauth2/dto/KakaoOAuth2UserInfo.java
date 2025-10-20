package com.project.demo.common.oauth2.dto;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class KakaoOAuth2UserInfo extends OAuth2UserInfo {

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
        log.info("KakaoOAuth2UserInfo 생성 - 전체 attributes: {}", attributes);
    }

    @Override
    public String getId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getNickname() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");
        log.info("Kakao account 정보: {}", account);

        if (account == null) {
            log.warn("Kakao account 정보가 null입니다.");
            return null;
        }

        Map<String, Object> profile = (Map<String, Object>) account.get("profile");
        log.info("Kakao profile 정보: {}", profile);

        if (profile == null) {
            log.warn("Kakao profile 정보가 null입니다.");
            return null;
        }

        String nickname = (String) profile.get("nickname");
        log.info("카카오에서 받은 닉네임: '{}' (길이: {})", nickname, nickname != null ? nickname.length() : 0);
        
        return nickname;
    }

    @Override
    public String getImageUrl() {
        Map<String, Object> account = (Map<String, Object>) attributes.get("kakao_account");

        if (account == null) {
            return null;
        }

        Map<String, Object> profile = (Map<String, Object>) account.get("profile");

        if (profile == null) {
            return null;
        }

        return (String) profile.get("thumbnail_image_url");
    }
}