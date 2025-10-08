package com.project.demo.domain.user.service;

import com.project.demo.domain.auth.entity.RefreshToken;
import com.project.demo.domain.auth.repository.RefreshTokenRepository;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.response.SignUpResponse;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

//    /*
//    회원가입 메서드
//     */
//    @Transactional
//    public SignUpResponse signUp(SignUpRequest signUpRequest) {
//
//        String email = signUpRequest.getEmail();
//        String name = signUpRequest.getName();
//        String password = signUpRequest.getPassword();
//        UserRole userRole = UserRole.of(signUpRequest.getUserRole());
//
//        // 이미 있는 이메일인지 확인
//        validateDuplicateEmail(email);
//
//        // 이미 있는 닉네임인지 확인
//        validateDuplicateName(name);
//
//        // 이미 탈퇴한 적 있는 이메일인지 확인
//        validateDeletedEmail(email);
//
//        validateAdminToken(signUpRequest, userRole); // 관리자 계정 생성 시 관리자 인증 토큰이 맞는지 확인
//
//        String encodedPassword = passwordEncoder.encode(password);
//
//        // 유저(회원) 생성
//        User newUser = User.builder()
//                .name(name)
//                .email(email)
//                .password(encodedPassword)
//                .userRole(userRole)
//                .build();
//
//        User savedUser = userRepository.save(newUser); // DB에 회원 저장
//
//        String accessTokenValue = jwtUtil.createAccessToken(savedUser.getId(), email, userRole, name);// Access Token 생성
//        String refreshTokenValue = jwtUtil.createRefreshToken(savedUser.getId());// Refresh Token 생성
//
//        // Refresh 토큰 DB에 저장
//        RefreshToken refreshToken = RefreshToken.builder()
//                .key(savedUser.getId())
//                .value(refreshTokenValue)
//                .build();
//        refreshTokenRepository.save(refreshToken);
//
//        return new SignUpResponse(accessTokenValue, refreshTokenValue); // Access Token 값, Refresh Token 값 반환
//    }
//
//
//
//
//
//     /*
//        이미 회원가입되어 활동하고 있는 이메일인지 검증
//     */
//    public void validateDuplicateEmail(String email) {
//        if (userRepository.existsByEmail(email)) {
//            User user = userRepository.findByEmail(email).get();
//
//            // 만약 회원탈퇴되지 않은 이메일(계정)이라면
//            if (!user.isDeleted()) throw new ;
//        }
//    }




}
