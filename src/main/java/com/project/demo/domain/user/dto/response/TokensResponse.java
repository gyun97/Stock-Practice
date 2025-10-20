package com.project.demo.domain.user.dto.response;

import lombok.*;

@Getter
@RequiredArgsConstructor
public class TokensResponse {

    private final String accessToken;
    private final String refreshToken;

}
