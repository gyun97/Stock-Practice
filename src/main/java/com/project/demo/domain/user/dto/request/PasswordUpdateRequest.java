package com.project.demo.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordUpdateRequest {

    @NotBlank
    private String currentPassword; // 현재 비밀번호 확인용

    @NotBlank
    private String newPassword;  // 새 비밀번호

    @NotBlank
    private String checkNewPassword;  // 새 비밀번호 확인
}
