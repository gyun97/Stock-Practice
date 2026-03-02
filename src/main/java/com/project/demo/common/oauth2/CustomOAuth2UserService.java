package com.project.demo.common.oauth2;

import com.project.demo.common.oauth2.dto.CustomOAuth2User;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;

    private static final String NAVER = "naver";
    private static final String KAKAO = "kakao";
    private static final String GOOGLE = "google";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        log.info("CustomOAuth2UserService.loadUser() 실행 - OAuth2 로그인 요청 진입");

        // 기본 로직: access token으로 유저 정보 요청
        OAuth2User oAuth2User = super.loadUser(userRequest);

        log.info("OAuth2User Attributes: {}", oAuth2User.getAttributes());

        // 로그인 진행중인 서비스를 구분하는 ID -> 여러 개의 소셜 로그인할 때 사용하는 ID
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        SocialType socialType = getSocialType(registrationId);

        Map<String, Object> attributes = oAuth2User.getAttributes();

        // OAuth2 로그인 진행 시 키가 되는 필드값(Primary Key)
        // OAuth 로그인 시 키 값. 구글, 네이버, 카카오 등 각 다르기 때문에 변수로 받아서 넣음
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();

        // socialType에 따라 유저 정보를 통해 OAuthAttributes 객체 생성
        OAuthAttributes extractAttributes = OAuthAttributes.of(socialType, userNameAttributeName, attributes);

        // 유저 등록 or 로그인 (이메일 기준 통합 로직 포함)
        User createdUser = getUser(extractAttributes, socialType);

        getOrSavePortfolio(createdUser);

        return new CustomOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                extractAttributes.getNameAttributeKey(),
                createdUser.getId(),
                createdUser.getEmail(),
                createdUser.getName());
    }

    @Transactional
    private void getOrSavePortfolio(User createdUser) {
        Portfolio portfolio = portfolioRepository.findByUser(createdUser)
                .orElseGet(() -> Portfolio.builder()
                        .balance(10000000)
                        .totalAsset(10000000)
                        .totalQuantity(0)
                        // .avgReturnRate(0)
                        .holdCount(0)
                        .stockAsset(0)
                        .user(createdUser)
                        .build());

        portfolioRepository.save(portfolio);
    }

    @Transactional
    private User getUser(OAuthAttributes attributes, SocialType socialType) {
        String socialId = attributes.getOauth2UserInfo().getId();
        String email = attributes.getOauth2UserInfo().getEmail();

        // 1. 소셜 타입 + 소셜 ID로 사용자 찾기
        User findUser = userRepository.findBySocialTypeAndSocialId(socialType, socialId).orElse(null);

        if (findUser == null) {
            // 2. 소셜 ID로 못 찾았다면, 이메일로 기존 사용자 찾기 (계정 통합)
            if (email != null && !email.isBlank()) {
                findUser = userRepository.findByEmail(email).orElse(null);
            }

            if (findUser != null) {
                // 이메일이 같은 기존 사용자가 있다면, 현재 소셜 정보를 연결(Link)
                log.info("기존 이메일 계정 발견 ({}). 새로운 소셜 정보 연결: {}", email, socialType);
                findUser.updateSocialInfo(socialType, socialId);
                return findUser; // @Transactional에 의해 변경사항 자동 반영
            }

            // 3. 기존 주체도, 이메일도 없다면 신규 가입
            return saveUser(attributes, socialType);
        }

        return findUser;
    }

    private User saveUser(OAuthAttributes attributes, SocialType socialType) {
        User createdUser = attributes.toEntity(socialType, attributes.getOauth2UserInfo());
        return userRepository.save(createdUser);
    }

    private SocialType getSocialType(String registrationId) {
        if (NAVER.equals(registrationId)) {
            return SocialType.NAVER;
        }
        if (KAKAO.equals(registrationId)) {
            return SocialType.KAKAO;
        }
        return SocialType.GOOGLE;
    }
}
