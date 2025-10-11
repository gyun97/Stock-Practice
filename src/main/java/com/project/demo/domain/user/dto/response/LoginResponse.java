package com.project.demo.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Builder
public class LoginResponse {

    private final String accessToken;
    private final String refreshToken;
}
