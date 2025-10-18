//package com.project.demo.domain.user.service;
//
//import com.project.demo.domain.user.dto.response.LoginResponse;
//import com.project.demo.domain.user.entity.User;
//import com.project.demo.domain.user.repository.UserRepository;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
//import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
//import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.util.UriComponentsBuilder;
//
//import java.net.URI;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.Map;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class KakaoOauthService extends DefaultOAuth2UserService {
//
////    @Value("${KAKAO_CLIENT_ID}")
////    private String clientId;
////
////    @Value("${KAKAO_REDIRECT_URL}")
////    private String redirectUri;
////
////    @Value("${KAKAO_API_UNLINK_URL}")
////    private String unlinkUrl;
//
//    private final UserRepository userRepository;
//
//    @Override
//    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
//        OAuth2User oAuth2User = super.loadUser(userRequest);
//        String registrationId = userRequest.getClientRegistration().getRegistrationId(); // kakao
//        String userNameAttributeName = userRequest
//                .getClientRegistration()
//                .getProviderDetails()
//                .getUserInfoEndpoint()
//                .getUserNameAttributeName(); // id
//
//        Map<String, Object> attributes = oAuth2User.getAttributes();
//        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
//        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
//
//        String email = (String) kakaoAccount.get("email");
//        String nickname = (String) profile.get("nickname");
//        String profileImage = (String) profile.get("profile_image_url");
//
//        User user = userRepository.findByEmail(email)
//                .orElseGet(() -> userRepository.save(
//                        new User(email, nickname, profileImage)
//                ));
//
//        return new DefaultOAuth2User(
//                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
//                attributes,
//                userNameAttributeName
//        );
//    }
//
//
//
//
//}
