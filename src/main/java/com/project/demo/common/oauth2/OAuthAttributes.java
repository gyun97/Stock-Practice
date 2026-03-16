package com.project.demo.common.oauth2;

import com.project.demo.common.oauth2.dto.GoogleOAuth2UserInfo;
import com.project.demo.common.oauth2.dto.KakaoOAuth2UserInfo;
import com.project.demo.common.oauth2.dto.NaverOAuth2UserInfo;
import com.project.demo.common.oauth2.dto.OAuth2UserInfo;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@Getter
@Slf4j
public class OAuthAttributes {

    private String nameAttributeKey; // OAuth2 로그인 진행 시 키가 되는 필드 값, PK와 같은 의미
    private OAuth2UserInfo oauth2UserInfo; // 소셜 타입별 로그인 유저 정보(닉네임, 이메일, 프로필 사진 등)

    @Builder
    public OAuthAttributes(String nameAttributeKey, OAuth2UserInfo oauth2UserInfo) {
        this.nameAttributeKey = nameAttributeKey;
        this.oauth2UserInfo = oauth2UserInfo;
    }

    /**
     * SocialType에 맞는 메소드 호출하여 OAuthAttributes 객체 반환
     * 파라미터 : userNameAttributeName -> OAuth2 로그인 시 키(PK)가 되는 값 / attributes : OAuth
     * 서비스의 유저 정보들
     * 소셜별 of 메소드(ofGoogle, ofKaKao, ofNaver)들은 각각 소셜 로그인 API에서 제공하는
     * 회원의 식별값(id), attributes, nameAttributeKey를 추출하여 build
     */
    public static OAuthAttributes of(SocialType socialType, String userNameAttributeName,
            Map<String, Object> attributes) {
        if (socialType == SocialType.NAVER) {
            return ofNaver(userNameAttributeName, attributes);
        }
        if (socialType == SocialType.KAKAO) {
            return ofKakao(userNameAttributeName, attributes);
        }
        return ofGoogle(userNameAttributeName, attributes);
    }

    private static OAuthAttributes ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .nameAttributeKey(userNameAttributeName)
                .oauth2UserInfo(new GoogleOAuth2UserInfo(attributes))
                .build();
    }

    private static OAuthAttributes ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .nameAttributeKey(userNameAttributeName)
                .oauth2UserInfo(new KakaoOAuth2UserInfo(attributes))
                .build();
    }

    private static OAuthAttributes ofNaver(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuthAttributes.builder()
                .nameAttributeKey(userNameAttributeName)
                .oauth2UserInfo(new NaverOAuth2UserInfo(attributes))
                .build();
    }

    /**
     * of메소드로 OAuthAttributes 객체가 생성되어, 로그인 시점에서 필요한 데이터만 추출하여
     * User 엔티티 생성
     * User 엔티티 생성 시점에서 회원가입
     */
    public User toEntity(SocialType socialType, OAuth2UserInfo oauth2UserInfo) {
        log.info("Creating new user entity for social type: {}", socialType);

        String email = oauth2UserInfo.getEmail();
        if (email == null || email.isBlank()) {
            email = UUID.randomUUID() + "@socialUser.com";
            log.warn("OAuth provider didn't return an email. Using random UUID email: {}", email);
        }

        return User.builder()
                .socialType(socialType)
                .socialId(oauth2UserInfo.getId())
                .email(email)
                .name(oauth2UserInfo.getNickname())
                .profileImage(oauth2UserInfo.getImageUrl())
                .userRole(UserRole.ROLE_USER)
                .build();
    }
}
