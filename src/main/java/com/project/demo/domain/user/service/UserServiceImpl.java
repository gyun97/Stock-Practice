package com.project.demo.domain.user.service;

import com.project.demo.common.exception.auth.*;
import com.project.demo.common.exception.auth.IncorrectPasswordException;
import com.project.demo.common.exception.user.InValidNewPasswordException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.domain.user.dto.request.PasswordUpdateRequest;
import com.project.demo.domain.user.dto.response.GetUserResponse;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.entity.RefreshToken;
import com.project.demo.domain.user.repository.RefreshTokenRepository;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.SignUpResponse;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/*
Refresh Token을 통한 Access Token 재발급 서비스
*/
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_TOKEN}")
    private String ADMIN_TOKEN; // 관리자가 맞는지 확인 토큰

    /*
    회원가입 메서드
     */
    @Transactional
    public SignUpResponse signUp(SignUpRequest signUpRequest) {

        String email = signUpRequest.getEmail();
        String name = signUpRequest.getName();
        String password = signUpRequest.getPassword();
        UserRole userRole = UserRole.of(signUpRequest.getUserRole());

        // 닉네임 중복 체크
        validateDuplicateName(name);

        // 관리자 토큰 검증
        validateAdminToken(signUpRequest, signUpRequest.getUserRole());

        User user;

        if (validateDuplicateEmail(email)) { // 이미 있는 이메일이면
            user = userRepository.findByEmail(email).orElseThrow();

            if (user.isDeleted()) { // 탈퇴 상태인 기존 회원이었다면
                user.reactivate(passwordEncoder.encode(password), name, userRole); // 기존 탈퇴 유저 복구
            } else {
                throw new DuplicateEmailException(); // 이미 해당 이메일 계정 존재
            }
        } else {
            // 신규 유저 생성
            user = User.createNewUser(email, name, passwordEncoder.encode(password), userRole);
        }

        // DB 저장 (신규 생성/복구 둘 다)
        User savedUser = userRepository.save(user);

        // 공통 토큰 발급 로직
        return issueTokens(savedUser);
    }

    /*
    로그인 처리 메서드
     */
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        String inputEmail = loginRequest.getEmail();
        String inputPassword = loginRequest.getPassword();

        log.info("입력한 계정: {}", inputEmail);
        log.info("입력한 비밀번호: {}", inputPassword);

        // 해당 이메일 계정의 유저가 존재하는지 확인
        User user = userRepository.findByEmail(inputEmail)
                .orElseThrow(() -> new NotFoundUserException());

        // 탈퇴한 계정인지 확인
        checkDeletedUser(user);

        String correctPassword = user.getPassword();

        // 비밀번호 검증
        validateCorrectPassword(inputPassword, correctPassword);

        // Access Token, Refresh Token 발급
        SignUpResponse tokens = issueTokens(user);

        return new LoginResponse(tokens.getAccessToken(), tokens.getRefreshToken()); // Access Token, Refresh Token 반환
    }

    /*
    유저 회원 탈퇴
     */
    @Transactional
    public String deleteUser(Long userId, String inputPassword) {

        // PK로 유저 조회
        User user = getUserById(userId);

        // 비밀번호 검증
        validateCorrectPassword(inputPassword, user.getPassword());

        // Refresh Token 삭제
        refreshTokenRepository.deleteById(userId);

        // 탈퇴 처리(is_deleted : true)
        user.updateIsDeleted();

        return "PK ID " + userId + "인 유저가 탈퇴처리되었습니다.";
    }

    private User getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundUserException());
        return user;
    }

    /*
    비밀번호 변경
     */
    @Transactional
    public String updatePassword(AuthUser authUser, PasswordUpdateRequest passwordUpdateRequest) {

        // 바꾸려고 입력한 두 동일한 새 비밀번호가 일치하는지 확인
        if (!passwordUpdateRequest.getNewPassword().equals(passwordUpdateRequest.getCheckNewPassword())) {
            throw new NewPasswordMismatch();
        }

        // 유저 가져오기
        User user = getUserById(authUser.getUserId());

        // 비밀번호 검증
        validateCorrectPassword(passwordUpdateRequest.getCurrentPassword(), user.getPassword());

        // 새 비밀번호가 기존 비밀번호와 다른지 확인
        if (passwordUpdateRequest.getCurrentPassword().equals(passwordUpdateRequest.getNewPassword())) {
            throw new InValidNewPasswordException();
        }

        // 새로운 비밀번호 변경
        user.changePassword(passwordEncoder.encode(passwordUpdateRequest.getNewPassword()));

        return "PK ID " + user.getId() + "인 유저의 비밀번호가 변경되었습니다.";
    }

    /*
    로그인, 회원가입한 유저에게 JWT 토큰 발급
     */
    @Transactional
    public SignUpResponse issueTokens(User savedUser) {
        String accessToken = jwtUtil.createAccessToken(savedUser.getId(), savedUser.getEmail(), savedUser.getUserRole(), savedUser.getName());
        String refreshTokenValue = jwtUtil.createRefreshToken(savedUser.getId());

        refreshTokenRepository.deleteById(savedUser.getId()); // 기존 Refresh Token 존재하면 삭제
        refreshTokenRepository.save(RefreshToken.builder() //새 Refresh Token 저장
                .key(savedUser.getId())
                .value(refreshTokenValue)
                .build());

        log.info("Access Token: {}", accessToken);
        log.info("Refresh Token: {}", refreshTokenValue);

        return new SignUpResponse(accessToken, refreshTokenValue);
    }

    /*
    유저 개인정보 조회
     */
    public GetUserResponse getUserInfo(Long userId) {
        User user = getUserById(userId);
        GetUserResponse userInfo = GetUserResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .balance(user.getBalance())
                .build();

        return userInfo;
    }

    /*
       이미 회원가입되어 활동하고 있는 이메일인지 검증
    */
    public boolean validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            return true;
        }
        return false;
    }

    /*
        이미 사용하고 있는 이름(닉네임)인지 확인
     */
    public void validateDuplicateName(String name) {
        if (userRepository.existsByName(name)) {
            throw new DuplicateNameException();
        }
    }

    /*
    회원가입시 해당 계정이 관리자 계정으로 가입하는지 확인
     */
    public void validateAdminToken(SignUpRequest signUpRequest, String userRole) {
        if (signUpRequest.getUserRole().equals("ROLE_ADMIN") && !(ADMIN_TOKEN.equals(signUpRequest.getAdminToken()))) {
            throw new InvalidAdminTokenException();
        }
    }

    /*
    Refresh Token을 이용한 Aceess Token 재발급 메서드
    */
    public String refreshAccessToken(String refreshToken) {

        // 1.  Refresh Token 유효성(유효 기간 및 서명) 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new InvalidTokenException();
        }

        // 2. 해당 Refresh Token이 DB에 실제 존재하는지 확인
        Long userId = Long.parseLong(jwtUtil.extractClaims(refreshToken).getSubject());
        isValid(userId, refreshToken);

        // 3. 새 Access Token 발급
        String email = jwtUtil.extractClaims(refreshToken).get("email", String.class);
        String role = jwtUtil.extractClaims(refreshToken).get("userRole", String.class);
        String name = jwtUtil.extractClaims(refreshToken).get("name", String.class);

        return jwtUtil.createAccessToken(userId, email, UserRole.of(role), name);
    }

    /*
    DB에 있는 Refresh Token과 클라이언트의 Refresh Token 비교 검증
     */
    public void isValid(Long userId, String refreshToken) {
        RefreshToken existingToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new NotFoundTokenException());

    }

    /*
    비밀번호 검증
     */
    public void validateCorrectPassword(String inputPassword, String correctPassword) {
        log.info("입력 비밀번호: {}", inputPassword);
        log.info("정확한 비밀번호: {}", correctPassword);

        if (!passwordEncoder.matches(inputPassword, correctPassword)) {
            throw new IncorrectPasswordException();
        }
    }

    /*
    탈퇴한 계정인지 확인
     */
    public void checkDeletedUser(User user) {
        if (user.isDeleted()) throw new NotFoundUserException();
    }


}
