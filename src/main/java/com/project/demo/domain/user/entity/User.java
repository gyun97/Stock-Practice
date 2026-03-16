package com.project.demo.domain.user.entity;

import com.project.demo.common.oauth2.SocialType;
import com.project.demo.common.util.TimeStamped;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.enums.UserRole;
import com.project.demo.domain.userstock.entity.UserStock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends TimeStamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    private String password;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    private UserRole userRole; // 운영자/일반 유저

    @Enumerated(EnumType.STRING)
    private SocialType socialType; // OAuth 주체(kakao", "naver", "google", "local")

    private String socialId;

    @Column(length = 100, unique = true, nullable = false)
    private String email;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String profileImage;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserStock> userStocks = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Portfolio portfolio;

    @Builder
    public User(Long id, String password, String name, UserRole userRole, String email,
            SocialType socialType, String socialId, String profileImage) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.userRole = userRole;
        this.email = email;
        this.socialType = socialType;
        this.socialId = socialId;
        this.profileImage = profileImage;
    }

    /*
     * 유저 회원가입시 새 유저 객체 생성
     */
    @Builder
    public static User createNewUser(String email, String name, String encodedPassword, UserRole role,
            SocialType socialType, String profileImage) {
        return User.builder()
                .email(email)
                .name(name)
                .password(encodedPassword)
                .userRole(role)
                .socialType(socialType)
                .profileImage(profileImage)
                .build();
    }

    /*
     * 유저 개인 정보 수정
     */
    public void updateUserInfo(UpdateUserInfoRequest request) {
        if (request.getNewEmail() != null)
            this.email = request.getNewEmail();
        if (request.getNewName() != null)
            this.name = request.getNewName();
        if (request.getNewProfileImage() != null)
            this.profileImage = request.getNewProfileImage();
    }

    /**
     * 소셜 정보 업데이트 (계정 통합용)
     */
    public void updateSocialInfo(SocialType socialType, String socialId) {
        this.socialType = socialType;
        this.socialId = socialId;
    }

    /*
     * 비밀번호 변경
     */
    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    /**
     * 프로필 이미지 업데이트
     */
    public void updateProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    /*
     * Spring Security 권한 정보 반환
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(userRole.name()));
    }
}
