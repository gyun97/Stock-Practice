package com.project.demo.domain.auth.service;

import com.project.demo.domain.user.dto.request.LoginRequest;
import com.project.demo.domain.user.dto.request.SignUpRequest;
import com.project.demo.domain.user.dto.response.LoginResponse;
import com.project.demo.domain.user.dto.response.SignUpResponse;
import com.project.demo.domain.user.entity.User;

public interface AuthService {

    public SignUpResponse signUp(SignUpRequest request);

    public String refreshAccessToken(String refreshToken);

    public void isValid(Long userId, String refreshToken);

    public SignUpResponse issueTokens(User user);

    public void validateDuplicateName(String name);

    public LoginResponse login(LoginRequest loginRequest);

    public void deleteUser(LoginRequest loginRequest);
}
