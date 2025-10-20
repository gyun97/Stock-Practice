package com.project.demo.domain.user.service;

import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.PasswordUpdateRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.request.UpdateUserInfoRequest;
import com.project.demo.domain.user.dto.response.GetUserResponse;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.TokensResponse;
import com.project.demo.domain.user.entity.AuthUser;
import com.project.demo.domain.user.entity.User;

public interface UserService {

    public LoginResponse signUp(SignUpRequest request);

    public String refreshAccessToken(String refreshToken);

    public void isValid(Long userId, String refreshToken);

    public TokensResponse issueTokens(User user);

    public void validateDuplicateName(String name);

    public LoginResponse login(LoginRequest loginRequest);

    public String deleteUser(Long userId, String inputPassword);

    public String updatePassword(AuthUser authUser, PasswordUpdateRequest passwordUpdateRequest);

    public GetUserResponse getUserInfo(Long userId);

    public GetUserResponse updateUserInfo(Long userId, UpdateUserInfoRequest request);

}
