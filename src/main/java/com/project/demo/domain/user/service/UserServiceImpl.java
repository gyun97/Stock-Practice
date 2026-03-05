package com.project.demo.domain.user.service;

import com.project.demo.common.exception.auth.*;
import com.project.demo.common.exception.user.InValidNewPasswordException;
import com.project.demo.common.exception.user.NotFoundUserException;
import com.project.demo.common.jwt.JwtUtil;
import com.project.demo.common.oauth2.SocialType;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.PasswordUpdateRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.dto.response.GetUserResponse;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.TokensResponse;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.entity.RefreshToken;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.user.repository.RefreshTokenRepository;
import com.project.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_TOKEN}")
    private String ADMIN_TOKEN; // 관리자가 맞는지 확인 토큰

    /*
     * 회원가입 메서드
     */
    @Transactional
    public LoginResponse signUp(SignUpRequest signUpRequest) {

        String email = signUpRequest.getEmail();
        String name = signUpRequest.getName();
        String password = signUpRequest.getPassword();

        // null 체크 포함해서 UserRole 안전하게 처리
        String roleStr = signUpRequest.getUserRole();
        UserRole userRole = (roleStr == null || roleStr.isBlank())
                ? UserRole.ROLE_USER
                : UserRole.of(roleStr);

        // 닉네임 중복 체크
        validateDuplicateName(name);

        // 관리자 토큰 검증
        if (!signUpRequest.getAdminToken().equals("")) {
            validateAdminToken(signUpRequest, signUpRequest.getUserRole());
        }

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
            user = User.createNewUser(email, name, passwordEncoder.encode(password), userRole, SocialType.LOCAL, "");
        }

        // DB 저장 (신규 생성/복구 둘 다)
        User savedUser = userRepository.save(user);

        // 공통 토큰 발급 로직
        TokensResponse tokens = issueTokens(savedUser);

        // 포트폴리오 처리: 탈퇴한 사용자 재가입 시 기존 포트폴리오 유지, 신규 사용자는 새로 생성
        Portfolio portfolio = portfolioRepository.findByUser(savedUser).orElse(null);
        if (portfolio == null) {
            // 신규 유저의 초기 포트폴리오 생성
            Portfolio newPortfolio = Portfolio.builder()
                    .balance(100000000)
                    .totalAsset(100000000)
                    .totalQuantity(0)
                    // .avgReturnRate(0)
                    .holdCount(0)
                    .stockAsset(0)
                    .user(savedUser)
                    .build();
            portfolioRepository.save(newPortfolio);
        }
        // 탈퇴한 사용자 재가입 시 기존 포트폴리오는 그대로 유지됨

        return LoginResponse.builder()
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .build(); // Access Token, Refresh Token, 사용자 정보 반환

    }

    /*
     * 로그인 처리 메서드
     */
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        String inputEmail = loginRequest.getEmail();
        String inputPassword = loginRequest.getPassword();

        log.info("입력한 계정: {}", inputEmail);
        log.info("입력한 비밀번호: {}", inputPassword);

        // 해당 이메일 계정의 유저가 존재하는지 확인
        User user = userRepository.findByEmail(inputEmail)
                .orElseThrow(NotFoundUserException::new);

        // 탈퇴한 계정인지 확인
        checkDeletedUser(user);

        String correctPassword = user.getPassword();

        // 비밀번호 검증
        validateCorrectPassword(inputPassword, correctPassword);

        // Access Token, Refresh Token 발급
        TokensResponse tokens = issueTokens(user);

        return LoginResponse.builder()
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImage(user.getProfileImage())
                .build(); // Access Token, Refresh Token, 사용자 정보 반환
    }

    /*
     * 유저 로그아웃
     */
    @Transactional
    public void logout(Long userId, String refreshToken) {
        // 현재 기기의 세션(토큰)만 삭제 (다른 기기 세션 유지)
        refreshTokenRepository.deleteByUserIdAndValue(userId, refreshToken);
    }

    /*
     * 유저 회원 탈퇴
     */
    @Transactional
    public String deleteUser(Long userId) {
        // PK로 유저 조회
        User user = getUserById(userId);

        // 모든 기기의 Refresh Token 삭제
        refreshTokenRepository.deleteAllByUserId(userId);

        // 탈퇴 처리(is_deleted : true)
        user.updateIsDeleted();

        return "PK ID " + userId + "인 유저가 탈퇴처리되었습니다.";
    }

    /*
     * 비밀번호 변경
     */
    @Transactional
    public String updatePassword(AuthUser authUser, PasswordUpdateRequest request) {

        // 바꾸려고 입력한 두 동일한 새 비밀번호가 일치하는지 확인
        if (!request.getNewPassword().equals(request.getCheckNewPassword())) {
            throw new NewPasswordMismatch();
        }

        // 유저 가져오기
        User user = getUserById(authUser.getUserId());

        // 비밀번호 검증
        validateCorrectPassword(request.getCurrentPassword(), user.getPassword());

        // 새 비밀번호가 기존 비밀번호와 다른지 확인
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new InValidNewPasswordException();
        }

        // 새로운 비밀번호 변경
        user.changePassword(passwordEncoder.encode(request.getNewPassword()));

        return "PK ID " + user.getId() + "인 유저의 비밀번호가 변경되었습니다.";
    }

    /*
     * 로그인, 회원가입한 유저에게 JWT 토큰 발급
     */
    @Transactional
    public TokensResponse issueTokens(User savedUser) {
        String accessToken = jwtUtil.createAccessToken(savedUser.getId(), savedUser.getEmail(), savedUser.getUserRole(),
                savedUser.getName());
        String refreshTokenValue = jwtUtil.createRefreshToken(savedUser.getId());

        // 기기별 독립 세션 생성 (UUID로 각 기기를 구분, 기존 다른 기기 세션은 유지)
        refreshTokenRepository.save(RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .userId(savedUser.getId())
                .value(refreshTokenValue)
                .build());

        log.info("Access Token: {}", accessToken);
        log.info("Refresh Token: {}", refreshTokenValue);

        return new TokensResponse(accessToken, refreshTokenValue);
    }

    /*
     * 유저 개인정보 조회
     */
    public GetUserResponse getUserInfo(Long userId) {
        User user = getUserById(userId);
        Portfolio portfolio = portfolioRepository.findByUser(user).orElseThrow();

        return GetUserResponse.of(user, portfolio.getBalance());
    }

    /*
     * 유저 개인 정보 수정
     */
    @Transactional
    public GetUserResponse updateUserInfo(Long userId, UpdateUserInfoRequest request) {
        User user = getUserById(userId);

        // 유저 정보 수정
        user.updateUserInfo(request);

        Portfolio portfolio = portfolioRepository.findByUser(user).orElseThrow();

        return GetUserResponse.of(user, portfolio.getBalance());
    }

    /*
     * ID로 유저 객체 가져오기
     */
    private User getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(NotFoundUserException::new);
        return user;
    }

    /*
     * 이미 회원가입되어 활동하고 있는 이메일인지 검증
     */
    public boolean validateDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            return true;
        }
        return false;
    }

    /*
     * 이미 사용하고 있는 이름(닉네임)인지 확인
     */
    public void validateDuplicateName(String name) {

        if (userRepository.existsByName(name)) {
            User user = userRepository.findByName(name).get();

            if (!user.isDeleted())
                throw new DuplicateNameException();
        }
    }

    /*
     * 회원가입시 해당 계정이 관리자 계정으로 가입하는지 확인
     */
    public void validateAdminToken(SignUpRequest signUpRequest, String userRole) {
        if (signUpRequest.getUserRole().equals("ROLE_ADMIN") && !(ADMIN_TOKEN.equals(signUpRequest.getAdminToken()))) {
            throw new InvalidAdminTokenException();
        }
    }

    /*
     * Refresh Token을 이용한 Access Token 및 Refresh Token 재발급 (RTR + 멀티 기기)
     */
    @Transactional
    public TokensResponse refreshAccessToken(String refreshToken) {

        // 1. Refresh Token 유효성(유효 기간 및 서명) 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new InvalidTokenException();
        }

        // 2. 해당 Refresh Token이 DB에 실제 존재하는 세션인지 확인 (토큰 값으로 조회)
        RefreshToken session = refreshTokenRepository.findByValue(refreshToken)
                .orElseThrow(NotFoundTokenException::new);

        // 3. 새 Access Token 및 Refresh Token 발급 후, 현재 세션의 토큰 값만 갱신 (RTR)
        User user = getUserById(session.getUserId());
        TokensResponse newTokens = issueTokens(user);

        // 4. 이전 세션(현재 기기)의 행 삭제 (issueTokens에서 새 행을 추가하므로 중복 방지)
        refreshTokenRepository.deleteById(session.getId());

        return newTokens;
    }

    /*
     * 비밀번호 검증
     */
    public void validateCorrectPassword(String inputPassword, String correctPassword) {
        log.info("입력 비밀번호: {}", inputPassword);

        if (!passwordEncoder.matches(inputPassword, correctPassword)) {
            throw new IncorrectPasswordException();
        }
    }

    /*
     * 탈퇴한 계정인지 확인
     */
    public void checkDeletedUser(User user) {
        if (user.isDeleted())
            throw new NotFoundUserException();
    }

}
