package com.project.demo.domain.user.dto.response;

import lombok.*;

@Getter
@RequiredArgsConstructor
@Builder
public class SignUpResponse {

    private final String accessToken;
    private final String refreshToken;

}
